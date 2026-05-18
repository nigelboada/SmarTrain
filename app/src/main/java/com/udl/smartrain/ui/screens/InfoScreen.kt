package com.udl.smartrain.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.udl.smartrain.data.local.AppLanguage
import com.udl.smartrain.ui.components.GlassCard

@Composable
fun InfoScreen(language: AppLanguage) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = infoTitle(language),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
        item {
            InfoCard(
                title = modeTitle(language),
                body = modeBody(language)
            )
        }
        item {
            InfoCard(
                title = ragTitle(language),
                body = ragBody(language)
            )
        }
        item {
            InfoCard(
                title = usageTitle(language),
                body = usageBody(language)
            )
        }
        item {
            InfoCard(
                title = limitsTitle(language),
                body = limitsBody(language)
            )
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(2.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.82f)
            )
        }
    }
}

private fun infoTitle(language: AppLanguage): String = when (language) {
    AppLanguage.CATALAN -> "Ajuda"
    AppLanguage.ENGLISH -> "Help"
    AppLanguage.SPANISH -> "Ayuda"
    AppLanguage.CHINESE -> "\u5e2e\u52a9"
}

private fun modeTitle(language: AppLanguage): String = when (language) {
    AppLanguage.CATALAN -> "Modes de resum"
    AppLanguage.ENGLISH -> "Summary modes"
    AppLanguage.SPANISH -> "Modos de resumen"
    AppLanguage.CHINESE -> "\u603b\u7ed3\u6a21\u5f0f"
}

private fun modeBody(language: AppLanguage): String = when (language) {
    AppLanguage.CATALAN -> "Resum local: ràpid i sempre disponible. IA cloud: millor redacció amb model remot. Backend RAG: opció més completa, perquè afegeix fonts, chunks, scores i FAQ sobre la sessió."
    AppLanguage.ENGLISH -> "Local summary: fast and always available. Cloud AI: better wording with a remote model. RAG backend: the most complete option, with sources, chunks, scores and session FAQ."
    AppLanguage.SPANISH -> "Resumen local: rapido y siempre disponible. IA cloud: mejor redaccion con modelo remoto. Backend RAG: opcion mas completa, con fuentes, chunks, scores y FAQ de sesion."
    AppLanguage.CHINESE -> "\u672c\u5730\u603b\u7ed3\uff1a\u5feb\u901f\u4e14\u59cb\u7ec8\u53ef\u7528\u3002\u4e91\u7aef AI\uff1a\u4f7f\u7528\u8fdc\u7a0b\u6a21\u578b\u751f\u6210\u66f4\u597d\u7684\u6587\u672c\u3002RAG \u540e\u7aef\uff1a\u6700\u5b8c\u6574\uff0c\u542b\u6765\u6e90\u3001chunks\u3001scores \u548c\u8bad\u7ec3 FAQ\u3002"
}

private fun ragTitle(language: AppLanguage): String = when (language) {
    AppLanguage.CATALAN -> "Com ajuda el RAG"
    AppLanguage.ENGLISH -> "How RAG helps"
    AppLanguage.SPANISH -> "Como ayuda el RAG"
    AppLanguage.CHINESE -> "RAG \u5982\u4f55\u5e2e\u52a9"
}

private fun ragBody(language: AppLanguage): String = when (language) {
    AppLanguage.CATALAN -> "El model ML detecta patrons de moviment. El RAG recupera documents del projecte i els usa per explicar què vol dir el resultat, quines limitacions té i què pots fer després."
    AppLanguage.ENGLISH -> "The ML model detects movement patterns. RAG retrieves project documents to explain the result, its limitations and what you can do next."
    AppLanguage.SPANISH -> "El modelo ML detecta patrones de movimiento. El RAG recupera documentos del proyecto para explicar el resultado, sus limitaciones y que puedes hacer despues."
    AppLanguage.CHINESE -> "ML \u6a21\u578b\u68c0\u6d4b\u8fd0\u52a8\u6a21\u5f0f\u3002RAG \u68c0\u7d22\u9879\u76ee\u6587\u6863\uff0c\u7528\u4e8e\u89e3\u91ca\u7ed3\u679c\u3001\u5c40\u9650\u548c\u540e\u7eed\u5efa\u8bae\u3002"
}

private fun usageTitle(language: AppLanguage): String = when (language) {
    AppLanguage.CATALAN -> "Recomanació d'ús"
    AppLanguage.ENGLISH -> "Usage recommendation"
    AppLanguage.SPANISH -> "Recomendacion de uso"
    AppLanguage.CHINESE -> "\u4f7f\u7528\u5efa\u8bae"
}

private fun usageBody(language: AppLanguage): String = when (language) {
    AppLanguage.CATALAN -> "Per fer una demo completa, usa Backend RAG. Per provar sense xarxa, usa Resum local. Si només vols una resposta ràpida i natural, usa IA cloud."
    AppLanguage.ENGLISH -> "For a full demo, use RAG backend. To test without network, use Local summary. For a quick natural answer, use Cloud AI."
    AppLanguage.SPANISH -> "Para una demo completa, usa Backend RAG. Para probar sin red, usa Resumen local. Si quieres una respuesta natural rapida, usa IA cloud."
    AppLanguage.CHINESE -> "\u5b8c\u6574\u6f14\u793a\u8bf7\u7528 RAG \u540e\u7aef\u3002\u65e0\u7f51\u6d4b\u8bd5\u8bf7\u7528\u672c\u5730\u603b\u7ed3\u3002\u5feb\u901f\u81ea\u7136\u56de\u7b54\u8bf7\u7528\u4e91\u7aef AI\u3002"
}

private fun limitsTitle(language: AppLanguage): String = when (language) {
    AppLanguage.CATALAN -> "Limitacions"
    AppLanguage.ENGLISH -> "Limitations"
    AppLanguage.SPANISH -> "Limitaciones"
    AppLanguage.CHINESE -> "\u5c40\u9650"
}

private fun limitsBody(language: AppLanguage): String = when (language) {
    AppLanguage.CATALAN -> "El model està basat en UCI HAR, no en dades específiques de futbol. Les recomanacions són orientatives i no substitueixen criteri mèdic o tècnic professional."
    AppLanguage.ENGLISH -> "The model is based on UCI HAR, not football-specific data. Recommendations are informative and do not replace medical or professional coaching advice."
    AppLanguage.SPANISH -> "El modelo se basa en UCI HAR, no en datos especificos de futbol. Las recomendaciones son orientativas y no sustituyen criterio medico o tecnico profesional."
    AppLanguage.CHINESE -> "\u6a21\u578b\u57fa\u4e8e UCI HAR\uff0c\u4e0d\u662f\u8db3\u7403\u4e13\u7528\u6570\u636e\u3002\u5efa\u8bae\u4ec5\u4f9b\u53c2\u8003\uff0c\u4e0d\u66ff\u4ee3\u533b\u7597\u6216\u4e13\u4e1a\u8bad\u7ec3\u610f\u89c1\u3002"
}
