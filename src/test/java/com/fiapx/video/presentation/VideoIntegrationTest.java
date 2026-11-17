package com.fiapx.video.presentation;

import com.fiapx.video.application.VideoApplicationService;
import com.fiapx.video.application.dto.ArquivoDownload;
import com.fiapx.video.application.port.EmailPort;
import com.fiapx.video.application.port.StoragePort;
import com.fiapx.video.domain.VideoStatus;
import com.fiapx.video.infrastructure.messaging.VideoEventoConsumer;
import com.fiapx.video.infrastructure.persistence.OutboxEventoEntity;
import com.fiapx.video.infrastructure.repository.EventoProcessadoRepository;
import com.fiapx.video.infrastructure.repository.NotificacaoRepository;
import com.fiapx.video.infrastructure.repository.OutboxEventoRepository;
import com.fiapx.video.infrastructure.repository.VideoRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class VideoIntegrationTest {

    private static final String SEGREDO = "fiapx-test-secret-key-with-at-least-32-bytes";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private VideoRepository videoRepository;
    @Autowired
    private OutboxEventoRepository outboxEventoRepository;
    @Autowired
    private EventoProcessadoRepository eventoProcessadoRepository;
    @Autowired
    private NotificacaoRepository notificacaoRepository;
    @Autowired
    private VideoEventoConsumer videoEventoConsumer;
    @Autowired
    private VideoApplicationService videoApplicationService;
    @Autowired
    private InMemoryStorage storage;
    @Autowired
    private RecordingEmail email;

    private MockMvc mockMvc;
    private UUID usuarioId;

    @BeforeEach
    void setUp() {
        notificacaoRepository.deleteAll();
        eventoProcessadoRepository.deleteAll();
        outboxEventoRepository.deleteAll();
        videoRepository.deleteAll();
        storage.limpar();
        email.limpar();
        usuarioId = UUID.randomUUID();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();
    }

    @Test
    @DisplayName("fluxo completo: upload, conclusao e download do ZIP")
    void deveExecutarFluxoCompleto() throws Exception {
        String videoId = upload("aula.mp4", "video/mp4", "conteudo");
        assertThat(videoRepository.findById(UUID.fromString(videoId))).isPresent();
        assertThat(outboxEventoRepository.findAll()).hasSize(1);
        assertThat(outboxEventoRepository.findAll().getFirst().getStatus())
            .isEqualTo(OutboxEventoEntity.Status.PENDENTE);
        assertThat(storage.chaves()).anyMatch(chave -> chave.startsWith("videos/" + usuarioId + "/"));

        String framesKey = "frames/" + videoId + ".zip";
        storage.salvar(framesKey, new ByteArrayInputStream("ZIPDATA".getBytes()), 7L, "application/zip");
        videoEventoConsumer.consumirProcessado(processadoEvento("evt-1", videoId, framesKey));

        mockMvc.perform(get("/api/videos/{id}", videoId).header("Authorization", bearer("USER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONCLUIDO"))
            .andExpect(jsonPath("$.downloadDisponivel").value(true));

        mockMvc.perform(get("/api/videos").header("Authorization", bearer("USER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElementos").value(1))
            .andExpect(jsonPath("$.conteudo[0].status").value("CONCLUIDO"));

        ArquivoDownload download = videoApplicationService.baixar(
            usuarioId, UUID.fromString(videoId), false);
        try (InputStream conteudo = download.conteudo()) {
            assertThat(conteudo.readAllBytes()).isEqualTo("ZIPDATA".getBytes(StandardCharsets.UTF_8));
        }
        assertThat(download.nomeArquivo()).isEqualTo("aula_frames.zip");
    }

    @Test
    @DisplayName("download responde no dispatch assincrono sem negar acesso")
    void deveBaixarNoDispatchAssincrono() throws Exception {
        String videoId = upload("aula.mp4", "video/mp4", "conteudo");
        String framesKey = "frames/" + videoId + ".zip";
        storage.salvar(framesKey, new ByteArrayInputStream("ZIPDATA".getBytes()), 7L, "application/zip");
        videoEventoConsumer.consumirProcessado(processadoEvento("evt-download", videoId, framesKey));

        MvcResult resultado = mockMvc.perform(get("/api/videos/{id}/download", videoId)
                .header("Authorization", bearer("USER")))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(resultado))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/zip"))
            .andExpect(content().bytes("ZIPDATA".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("evento de conclusao duplicado nao e aplicado duas vezes")
    void deveIgnorarEventoDuplicado() throws Exception {
        String videoId = upload("aula.mp4", "video/mp4", "conteudo");
        String framesKey = "frames/" + videoId + ".zip";
        storage.salvar(framesKey, new ByteArrayInputStream("ZIP".getBytes()), 3L, "application/zip");
        String evento = processadoEvento("evt-1", videoId, framesKey);

        videoEventoConsumer.consumirProcessado(evento);
        videoEventoConsumer.consumirProcessado(evento);

        assertThat(eventoProcessadoRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("falha no processamento muda status e agenda e-mail")
    void deveRegistrarFalhaENotificar() throws Exception {
        String videoId = upload("aula.mp4", "video/mp4", "conteudo");

        videoEventoConsumer.consumirFalha("""
            {"eventId":"evt-falha","videoId":"%s","motivo":"ffmpeg retornou 1",
             "ocorridoEm":"2026-01-01T10:00:00"}
            """.formatted(videoId));

        mockMvc.perform(get("/api/videos/{id}", videoId).header("Authorization", bearer("USER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ERRO"))
            .andExpect(jsonPath("$.erroMensagem").value("ffmpeg retornou 1"));
        assertThat(notificacaoRepository.findAll()).hasSize(1);
        assertThat(notificacaoRepository.findAll().getFirst().getDestinatario())
            .isEqualTo("maria@fiapx.com");
    }

    @Test
    @DisplayName("download de video pendente devolve 409")
    void deveRecusarDownloadPendente() throws Exception {
        String videoId = upload("aula.mp4", "video/mp4", "conteudo");

        mockMvc.perform(get("/api/videos/{id}/download", videoId)
                .header("Authorization", bearer("USER")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.erro").value("VIDEO_NAO_PROCESSADO"));
    }

    @Test
    @DisplayName("rejeita requisicao sem token")
    void deveExigirToken() throws Exception {
        mockMvc.perform(get("/api/videos"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rejeita arquivo nao suportado")
    void deveRejeitarArquivoInvalido() throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile(
            "arquivo", "nota.txt", "text/plain", "x".getBytes());

        mockMvc.perform(multipart("/api/videos").file(arquivo).header("Authorization", bearer("USER")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.erro").value("ARQUIVO_INVALIDO"));
    }

    @Test
    @DisplayName("nao expoe video de outro usuario")
    void deveIsolarVideosPorUsuario() throws Exception {
        String videoId = upload("aula.mp4", "video/mp4", "conteudo");
        UUID outroUsuario = UUID.randomUUID();

        mockMvc.perform(get("/api/videos/{id}", videoId)
                .header("Authorization", bearer(outroUsuario, "USER")))
            .andExpect(status().isNotFound());
    }

    private String upload(String nome, String contentType, String conteudo) throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile(
            "arquivo", nome, contentType, conteudo.getBytes(StandardCharsets.UTF_8));
        MvcResult result = mockMvc.perform(multipart("/api/videos")
                .file(arquivo)
                .header("Authorization", bearer("USER")))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("PENDENTE"))
            .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String processadoEvento(String eventId, String videoId, String framesKey) {
        return """
            {"eventId":"%s","videoId":"%s","framesZipKey":"%s","totalFrames":3,
             "processadoEm":"2026-01-01T10:00:00"}
            """.formatted(eventId, videoId, framesKey);
    }

    private String bearer(String role) {
        return bearer(usuarioId, role);
    }

    private String bearer(UUID id, String role) {
        String token = Jwts.builder()
            .subject(id.toString())
            .issuer("fiapx-auth")
            .issuedAt(new Date())
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .claim("nome", "Maria")
            .claim("email", "maria@fiapx.com")
            .claim("role", role)
            .signWith(Keys.hmacShaKeyFor(SEGREDO.getBytes(StandardCharsets.UTF_8)))
            .compact();
        return "Bearer " + token;
    }

    @TestConfiguration
    static class FakesConfig {

        @Bean
        @Primary
        InMemoryStorage inMemoryStorage() {
            return new InMemoryStorage();
        }

        @Bean
        @Primary
        RecordingEmail recordingEmail() {
            return new RecordingEmail();
        }
    }

    static class InMemoryStorage implements StoragePort {

        private final Map<String, byte[]> objetos = new ConcurrentHashMap<>();
        private final Map<String, String> tipos = new ConcurrentHashMap<>();

        @Override
        public void salvar(String chave, InputStream conteudo, long tamanhoBytes, String contentType) {
            try {
                objetos.put(chave, conteudo.readAllBytes());
                tipos.put(chave, contentType);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public RecursoArmazenado obter(String chave) {
            byte[] conteudo = objetos.get(chave);
            if (conteudo == null) {
                throw new IllegalStateException("objeto ausente: " + chave);
            }
            return new RecursoArmazenado(new ByteArrayInputStream(conteudo),
                conteudo.length, tipos.get(chave));
        }

        @Override
        public boolean existe(String chave) {
            return objetos.containsKey(chave);
        }

        @Override
        public void remover(String chave) {
            objetos.remove(chave);
            tipos.remove(chave);
        }

        List<String> chaves() {
            return new ArrayList<>(objetos.keySet());
        }

        void limpar() {
            objetos.clear();
            tipos.clear();
        }
    }

    static class RecordingEmail implements EmailPort {

        private final List<String> destinatarios = new ArrayList<>();

        @Override
        public void enviar(String destinatario, String assunto, String corpo) {
            destinatarios.add(destinatario);
        }

        void limpar() {
            destinatarios.clear();
        }
    }
}
