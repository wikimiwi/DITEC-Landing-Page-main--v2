package com.ditec.assistencia.service;

import com.ditec.assistencia.dto.admin.TecnicoCriadoResponse;
import com.ditec.assistencia.dto.admin.TecnicoRequest;
import com.ditec.assistencia.dto.admin.TecnicoResponse;
import com.ditec.assistencia.entity.Tecnico;
import com.ditec.assistencia.entity.Usuario;
import com.ditec.assistencia.enums.StatusConta;
import com.ditec.assistencia.enums.TipoUsuario;
import com.ditec.assistencia.exception.ConflitoException;
import com.ditec.assistencia.exception.RecursoNaoEncontradoException;
import com.ditec.assistencia.repository.TecnicoRepository;
import com.ditec.assistencia.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

/** CRUD de tecnicos, usado pelo painel administrativo (secao 14 do prompt mestre). */
@Service
@RequiredArgsConstructor
public class TecnicoService {

    private final TecnicoRepository tecnicoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String ALFABETO_SENHA = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789";

    public List<TecnicoResponse> listar() {
        return tecnicoRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public TecnicoCriadoResponse criar(TecnicoRequest req) {
        String email = req.email().trim().toLowerCase();
        if (usuarioRepository.existsByEmailIgnoreCase(email) || tecnicoRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflitoException("Ja existe uma conta com este e-mail.");
        }
        boolean senhaForiPorAdmin = req.senha() != null && req.senha().length() >= 6;
        String senhaInicial = senhaForiPorAdmin ? req.senha() : gerarSenhaTemporaria();

        Usuario usuario = Usuario.builder()
                .nome(req.nome().trim())
                .email(email)
                .senhaHash(passwordEncoder.encode(senhaInicial))
                .telefone(req.telefone())
                .tipo(TipoUsuario.TECNICO)
                .status(StatusConta.ATIVO)
                .build();
        usuario = usuarioRepository.save(usuario);

        Tecnico tecnico = Tecnico.builder()
                .usuario(usuario)
                .nome(req.nome().trim())
                .email(email)
                .telefone(req.telefone())
                .especialidade(req.especialidade())
                .certificacao(req.certificacao())
                .status(StatusConta.ATIVO)
                .build();
        tecnico = tecnicoRepository.save(tecnico);

        return new TecnicoCriadoResponse(toResponse(tecnico), senhaForiPorAdmin ? null : senhaInicial);
    }

    @Transactional
    public TecnicoResponse atualizar(Long id, TecnicoRequest req) {
        Tecnico tecnico = tecnicoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tecnico nao encontrado."));
        tecnico.setNome(req.nome().trim());
        tecnico.setTelefone(req.telefone());
        tecnico.setEspecialidade(req.especialidade());
        tecnico.setCertificacao(req.certificacao());
        tecnicoRepository.save(tecnico);

        Usuario usuario = tecnico.getUsuario();
        usuario.setNome(req.nome().trim());
        usuario.setTelefone(req.telefone());
        usuarioRepository.save(usuario);

        return toResponse(tecnico);
    }

    @Transactional
    public TecnicoResponse alterarStatus(Long id, boolean ativo) {
        Tecnico tecnico = tecnicoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tecnico nao encontrado."));
        StatusConta novo = ativo ? StatusConta.ATIVO : StatusConta.INATIVO;
        tecnico.setStatus(novo);
        tecnicoRepository.save(tecnico);
        tecnico.getUsuario().setStatus(novo);
        usuarioRepository.save(tecnico.getUsuario());
        return toResponse(tecnico);
    }

    private String gerarSenhaTemporaria() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder("Ditec");
        for (int i = 0; i < 6; i++) sb.append(ALFABETO_SENHA.charAt(random.nextInt(ALFABETO_SENHA.length())));
        return sb.toString();
    }

    private TecnicoResponse toResponse(Tecnico t) {
        return new TecnicoResponse(t.getId(), t.getNome(), t.getEmail(), t.getTelefone(),
                t.getEspecialidade(), t.getCertificacao(), t.getStatus().name());
    }
}
