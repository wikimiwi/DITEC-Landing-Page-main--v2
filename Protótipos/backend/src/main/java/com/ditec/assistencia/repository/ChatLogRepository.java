package com.ditec.assistencia.repository;

import com.ditec.assistencia.entity.ChatLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ChatLogRepository extends JpaRepository<ChatLog, Long> {
    List<ChatLog> findAllByOrderByDataHoraDesc();
    List<ChatLog> findBySessaoIdOrderByDataHoraAsc(String sessaoId);

    @Query("select count(distinct c.sessaoId) from ChatLog c")
    long countSessoesDistintas();
}
