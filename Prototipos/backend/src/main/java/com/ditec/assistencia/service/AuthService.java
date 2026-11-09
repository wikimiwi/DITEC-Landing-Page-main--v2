package com.ditec.assistencia.service;

import com.ditec.assistencia.dto.auth.AuthResponse;
import com.ditec.assistencia.dto.auth.LoginRequest;
import com.ditec.assistencia.dto.auth.RegisterRequest;
import com.ditec.assistencia.dto.auth.UsuarioMeResponse;
import com.ditec.assistencia.entity.Agendamento;
import com.ditec.assistencia.entity.Cliente;
import com.ditec.assistencia.entity.Usuario;
import com.ditec.assistencia.enums.StatusConta;
import com.ditec.assistencia.enums.TipoUsuario;
import com.ditec.assistencia.exception.ConflitoException;
import com.ditec.assistencia.exception.CredenciaisInvalidasException;
import com.ditec.assistencia.repository.AgendamentoRepository;
import com.ditec.assistencia.repository.ClienteRepository;
import com.ditec.assistencia.repository.OrdemServicoRepository;
import com.ditec.assistencia.repository.UsuarioRepository;
import com.ditec.assistencia.security.JwtProperties;
import com.ditec.assistencia.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final OrdemServicoRepository ordemServicoRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;

    private static final DateTimeFormatter DESDE_FMT = DateTimeFormatter.ofPattern("MMM yyyy", new Locale("pt", "BR"));

    @Transactional
    public AuthResponse registrar(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflitoException("Ja existe uma conta com este e-mail.");
        }

        Usuario usuario = Usuario.builder()
                .nome(req.nome().trim())
                .email(email)
                .senhaHash(passwordEncoder.encode(req.senha()))
                .telefone(req.telefone().trim())
                .tipo(TipoUsuario.CLIENTE)
                .status(StatusConta.ATIVO)
                .build();
        usuario = usuarioRepository.save(usuario);

        Cliente cliente = Cliente.builder().usuario(usuario).build();
        final Cliente clienteSalvo = clienteRepository.save(cliente);

        // Vincula o agendamento mais recente feito como visitante (mesmo telefone)
        // a conta recem-criada — fluxo "agendou e depois criou a conta".
        // Correcoes: (1) a OS tambem e' vinculada (antes so' o agendamento, e o
        // cliente nao via a propria OS); (2) so' vale para agendamentos das
        // ultimas 24h, para que alguem nao "herde" atendimentos antigos de
        // terceiros apenas informando o telefone deles.
        agendamentoRepository.findFirstByClienteIsNullAndTelefoneContatoOrderByCriadoEmDesc(usuario.getTelefone())
                .filter(ag -> ag.getCriadoEm() != null && ag.getCriadoEm().isAfter(LocalDateTime.now().minusHours(24)))
                .ifPresent(ag -> {
                    ag.setCliente(clienteSalvo);
                    agendamentoRepository.save(ag);
                    ordemServicoRepository.findByAgendamento_Id(ag.getId()).ifPresent(os -> {
                        os.setCliente(clienteSalvo);
                        ordemServicoRepository.save(os);
                    });
                });

        return gerarResposta(usuario);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(req.email().trim())
                .orElseThrow(() -> new CredenciaisInvalidasException("E-mail ou senha incorretos."));

        if (usuario.getStatus() != StatusConta.ATIVO) {
            throw new CredenciaisInvalidasException("Esta conta esta inativa. Fale com a DITEC.");
        }
        if (!passwordEncoder.matches(req.senha(), usuario.getSenhaHash())) {
            throw new CredenciaisInvalidasException("E-mail ou senha incorretos.");
        }

        usuario.setUltimoAcesso(LocalDateTime.now());
        usuarioRepository.save(usuario);

        return gerarResposta(usuario);
    }

    public UsuarioMeResponse me(Usuario usuario) {
        String desde = usuario.getDataCadastro() != null ? DESDE_FMT.format(usuario.getDataCadastro()) : "-";
        return new UsuarioMeResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(),
                usuario.getTelefone(), usuario.getTipo().name(), desde);
    }

    private AuthResponse gerarResposta(Usuario usuario) {
        String token = jwtUtil.gerarToken(usuario.getEmail(), usuario.getTipo().name(), usuario.getId());
        return new AuthResponse(token, usuario.getTipo().name(), usuario.getNome(), usuario.getEmail(),
                jwtProperties.expirationMinutes());
    }
}
