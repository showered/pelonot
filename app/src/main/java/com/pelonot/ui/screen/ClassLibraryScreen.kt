package com.pelonot.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pelonot.data.local.entity.ClassTemplateEntity

@Composable
fun ClassLibraryScreen(
    classTemplates: List<ClassTemplateEntity>,
    onClassSelected: (ClassTemplateEntity) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Class Library",
            style = androidx.compose.material3.MaterialTheme.typography.headlineLarge
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(classTemplates) { classTemplate ->
                ClassCard(
                    classTemplate = classTemplate,
                    onClick = { onClassSelected(classTemplate) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Back")
        }
    }
}

@Composable
private fun ClassCard(
    classTemplate: ClassTemplateEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = classTemplate.title,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                )
                Text(
                    text = classTemplate.category,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = "${classTemplate.durationSec / 60} min",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
            )
        }
    }
}