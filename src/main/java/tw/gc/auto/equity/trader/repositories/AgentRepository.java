package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.Agent;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentRepository extends JpaRepository<Agent, Long> {
    
    Optional<Agent> findByName(String name);
    
    List<Agent> findByStatus(Agent.AgentStatus status);
    
    List<Agent> findByAgentType(String agentType);
    
    boolean existsByName(String name);
}
