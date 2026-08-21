package com.ditec.assistencia.service;

import com.ditec.assistencia.dto.admin.ServicoRequest;
import com.ditec.assistencia.dto.admin.ServicoResponse;
import com.ditec.assistencia.entity.Servico;
import com.ditec.assistencia.enums.StatusConta;
import com.ditec.assistencia.exception.RecursoNaoEncontradoException;
import com.ditec.assistencia.repository.ServicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServicoService {

    private final ServicoRepository servicoRepository;

    public List<ServicoResponse> listarAtivos() {
        return servicoRepository.findAll().stream()
                .filter(s -> s.getStatus() == StatusConta.ATIVO)
                .map(this::toResponse).toList();
    }

    public List<ServicoResponse> listarTodos() {
        return servicoRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public ServicoResponse criar(ServicoRequest req) {
        Servico s = Servico.builder()
                .nome(req.nome().trim()).descricao(req.descricao())
                .valorBase(req.valorBase()).status(StatusConta.ATIVO).build();
        return toResponse(servicoRepository.save(s));
    }

    @Transactional
    public ServicoResponse atualizar(Long id, ServicoRequest req) {
        Servico s = servicoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Servico nao encontrado."));
        s.setNome(req.nome().trim());
        s.setDescricao(req.descricao());
        s.setValorBase(req.valorBase());
        return toResponse(servicoRepository.save(s));
    }

    @Transactional
    public ServicoResponse alterarStatus(Long id, boolean ativo) {
        Servico s = servicoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Servico nao encontrado."));
        s.setStatus(ativo ? StatusConta.ATIVO : StatusConta.INATIVO);
        return toResponse(servicoRepository.save(s));
    }

    private ServicoResponse toResponse(Servico s) {
        return new ServicoResponse(s.getId(), s.getNome(), s.getDescricao(), s.getValorBase(), s.getStatus().name());
    }
}
