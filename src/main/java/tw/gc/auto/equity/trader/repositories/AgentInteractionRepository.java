package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.AgentInteraction;
import tw.gc.auto.equity.trader.entities.AgentInteraction.InteractionType;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AgentInteractionRepository extends JpaRepository<AgentInteraction, Long> {
    
    List<AgentInteraction> findByAgentNameAndTimestampAfterOrderByTimestampDesc(
            String agentName, LocalDateTime since);
    
    @Query("SELECT COUNT(i) FROM AgentInteraction i WHERE i.agentName = :agentName " +
           "AND i.type = :type AND i.userId = :userId AND i.timestamp >= :since")
    long countInteractions(@Param("agentName") String agentName, 
                          @Param("type") InteractionType type,
                          @Param("userId") String userId,
                          @Param("since") LocalDateTime since);
    
    List<AgentInteraction> findByUserIdAndTimestampAfterOrderByTimestampDesc(
            String userId, LocalDateTime since);
}
