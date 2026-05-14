package com.udl.smartrain.ml

import com.udl.smartrain.domain.model.Session

interface RagAnswerGenerator {
    val provider: String
    val model: String

    suspend fun generate(session: Session, retrievedDocuments: List<RagDocument>): SessionRagInsight
}

object RuleBasedRagAnswerGenerator : RagAnswerGenerator {
    override val provider: String = "rules"
    override val model: String = "rule_based"

    override suspend fun generate(
        session: Session,
        retrievedDocuments: List<RagDocument>
    ): SessionRagInsight {
        return SessionRagRecommender.buildRuleBasedInsight(session, retrievedDocuments)
    }
}
