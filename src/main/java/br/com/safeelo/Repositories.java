package br.com.safeelo;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserRepository extends JpaRepository<AppUser, Long> {
  Optional<AppUser> findByEmail(String email);
}

interface RuleRepository extends JpaRepository<RiskRule, Long> {}

interface AnalysisRepository extends JpaRepository<ThreatAnalysis, Long> {
  Page<ThreatAnalysis> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable page);

  Optional<ThreatAnalysis> findByIdAndUserId(Long id, Long userId);

  long countByUserId(Long userId);
}

interface LinkRepository extends JpaRepository<EloLink, Long> {
  List<EloLink> findByOwnerIdOrderByCreatedAtDesc(Long id);

  List<EloLink> findBySupporterIdOrderByCreatedAtDesc(Long id);

  Optional<EloLink> findByTokenHash(String hash);

  Optional<EloLink> findByOwnerIdAndSupportEmail(Long id, String email);

  List<EloLink> findByOwnerIdAndState(Long id, String state);
}

interface AlertRepository extends JpaRepository<SupportAlert, Long> {
  List<SupportAlert> findByLinkIdInOrderByCreatedAtDesc(List<Long> ids);

  Optional<SupportAlert> findByLinkIdAndAnalysisId(Long link, Long analysis);

  void deleteByLinkId(Long id);
}

interface ResetRepository extends JpaRepository<PasswordReset, Long> {
  Optional<PasswordReset> findByTokenHash(String tokenHash);

  void deleteByUserId(Long userId);
}
