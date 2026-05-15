package com.udl.smartrain.ml

import com.udl.smartrain.domain.model.Session
import com.udl.smartrain.data.local.AppLanguage

interface RagAnswerGenerator {
    val provider: String
    val model: String

    suspend fun generate(session: Session, retrievedDocuments: List<RagDocument>, language: AppLanguage): SessionRagInsight
}

object RuleBasedRagAnswerGenerator : RagAnswerGenerator {
    override val provider: String = "rules"
    override val model: String = "rule_based"

    override suspend fun generate(
        session: Session,
        retrievedDocuments: List<RagDocument>,
        language: AppLanguage
    ): SessionRagInsight {
        return SessionRagRecommender.buildRuleBasedInsight(session, retrievedDocuments, language)
    }
}
