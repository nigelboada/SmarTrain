package com.udl.smartrain.ml

import com.udl.smartrain.domain.model.Session

interface RagAnswerGenerator {
    suspend fun generate(session: Session, retrievedDocuments: List<RagDocument>): SessionRagInsight
}

object RuleBasedRagAnswerGenerator : RagAnswerGenerator {
    override suspend fun generate(
        session: Session,
        retrievedDocuments: List<RagDocument>
    ): SessionRagInsight {
        return SessionRagRecommender.buildRuleBasedInsight(session, retrievedDocuments)
    }
}

