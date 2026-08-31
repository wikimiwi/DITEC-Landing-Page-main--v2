package com.ditec.assistencia.service;

import com.ditec.assistencia.dto.agendamento.AgendamentoResponse;
import com.ditec.assistencia.dto.cliente.ClientePerfilResponse;
import com.ditec.assistencia.dto.cliente.ClienteUpdateRequest;
import com.ditec.assistencia.entity.Agendamento;
import com.ditec.assistencia.entity.Cliente;
import com.ditec.assistencia.entity.Usuario;
import com.ditec.assistencia.exception.RecursoNaoEncontradoException;
import com.ditec.assistencia.repository.AgendamentoRepository;
import com.ditec.assistencia.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final AgendamentoRepository agendamentoRepository;

    private static final DateTimeFormatter DESDE_FMT = DateTimeFormatter.ofPattern("MMM yyyy", new Locale("pt", "BR"));

    public Cliente buscarClientePorUsuario(Usuario usuario) {
        return clienteRepository.findByUsuario_Id(usuario.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cadastro de cliente nao encontrado."));
    }

    public ClientePerfilResponse perfil(Usuario usuario) {
        Cliente c = buscarClientePorUsuario(usuario);
        return new ClientePerfilResponse(
                c.getId(), usuario.getNome(), usuario.getEmail(), usuario.getTelefone(),
                c.getEndereco(), c.getBairro(), c.getCidade(), c.getUf(), c.getCep(), c.getComplemento(),
                usuario.getDataCadastro() != null ? DESDE_FMT.format(usuario.getDataCadastro()) : "-"
        );
    }

    @Transactional
    public ClientePerfilResponse atualizarPerfil(Usuario usuario, ClienteUpdateRequest req) {
        Cliente c = buscarClientePorUsuario(usuario);
        usuario.setNome(req.nome().trim());
        usuario.setTelefone(req.telefone().trim());
        c.setEndereco(req.endereco());
        c.setBairro(req.bairro());
        c.setCidade(req.cidade());
        c.setUf(req.uf());
        c.setCep(req.cep());
        c.setComplemento(req.complemento());
        clienteRepository.save(c);
        return perfil(usuario);
    }

    public List<AgendamentoResponse> meusAgendamentos(Usuario usuario) {
        Cliente c = buscarClientePorUsuario(usuario);
        List<Agendamento> lista = agendamentoRepository.findByCliente_IdOrderByDataHoraDesc(c.getId());
        return lista.stream().map(AgendamentoMapper::toResponse).toList();
    }
}
