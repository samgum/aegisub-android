package io.github.samgum.aegisub.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samgum.aegisub.data.repository.Project
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主屏：项目列表 + 新建 FAB。Phase 1 占位 UI（真正的编辑器在 Phase 2+）。
 *
 * @author 伤感咩吖
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenProject: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Aegisub Android") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.createSampleProject() }) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        },
    ) { padding ->
        if (projects.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "暂无字幕工程\n点右下角 + 新建",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(projects, key = { it.id }) { project ->
                    ProjectRow(project, onClick = { onOpenProject(project.id) })
                }
            }
        }
    }
}

@Composable
private fun ProjectRow(project: Project, onClick: () -> Unit) {
    val ts = project.lastOpenedAt ?: project.updatedAt
    val date = remember(ts) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
    }
    ListItem(
        headlineContent = {
            Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = { Text("${project.format.uppercase()} · $date") },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
