package br.com.safeelo;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "app_user")
class AppUser {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  String name, email, passwordHash, role, phoneCipher;
  boolean autoShareHigh;
  Instant createdAt;
}

@Entity
@Table(name = "risk_rule")
class RiskRule {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  String category, expression, explanation;
  int weight;
}

@Entity
@Table(name = "threat_analysis")
class ThreatAnalysis {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  Long userId;
  String kind;

  @Column(columnDefinition = "TEXT")
  String contentCipher;

  int score;
  String level;

  @Column(columnDefinition = "TEXT")
  String reasonsJson;

  Instant createdAt;
}

@Entity
@Table(name = "elo_link")
class EloLink {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  Long ownerId, supporterId;
  String supportEmail, relationshipLabel, state, tokenHash;
  Instant expiresAt, createdAt;
}

@Entity
@Table(name = "support_alert")
class SupportAlert {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  Long linkId, analysisId;
  String state, emailState;
  Instant createdAt, acknowledgedAt;
}

@Entity
@Table(name = "password_reset")
class PasswordReset {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  Long userId;
  String tokenHash;
  Instant expiresAt;
  boolean used;
}
