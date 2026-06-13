package io.github.samgum.aegisub.ui.home

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samgum.aegisub.data.repository.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主屏：项目列表 + 新建 FAB + 导入（SAF OpenDocument）。
 *
 * @author 伤感咩吖
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenProject: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val (name, content) = readSubtitleFile(context, uri)
                viewModel.importSubtitle(name, content)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aegisub Android") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                    TextButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Text("导入")
                    }
                },
            )
        },
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
                    "暂无字幕工程\n点右下角 + 新建，或顶栏导入",
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

/**
 * 读取 SAF 返回的 URI：文件名（DISPLAY_NAME）+ 文本内容。IO 线程。
 * 失败时文件名回退到 URI 末段、内容回退空串（不崩）。
 */
private suspend fun readSubtitleFile(context: Context, uri: Uri): Pair<String, String> =
    withContext(Dispatchers.IO) {
        val name = context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: (uri.lastPathSegment ?: "字幕")
        val content = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: ""
        name to content
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
