package com.enziidict.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enziidict.app.data.local.dao.WordFolderWithCount
import com.enziidict.app.data.local.entity.WordFolder

val FOLDER_COLOR_PALETTE: List<String> = listOf(
    "#1D4ED8",
    "#0F766E",
    "#7C3AED",
    "#EA580C",
    "#DC2626",
    "#0284C7"
)

fun parseFolderColor(colorHex: String, fallback: Color): Color {
    return runCatching {
        Color(android.graphics.Color.parseColor(colorHex))
    }.getOrDefault(fallback)
}

fun formatSavedFoldersButtonLabel(savedFolders: List<WordFolder>): String {
    return when {
        savedFolders.isEmpty() -> "Lưu vào sổ tay"
        savedFolders.size == 1 -> "Đã lưu • ${savedFolders.first().name}"
        else -> "Đã lưu • ${savedFolders.first().name} (+${savedFolders.size - 1})"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveToFolderBottomSheet(
    word: String,
    folders: List<WordFolderWithCount>,
    savedFolderIds: Set<Long>,
    onDismiss: () -> Unit,
    onToggleFolder: (WordFolder) -> Unit,
    onCreateAndSave: (folderName: String, colorHex: String) -> Unit,
    onRemoveAll: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberEnziiHaptics()
    var showCreateForm by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var selectedColorHex by remember { mutableStateOf(FOLDER_COLOR_PALETTE[1]) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Lưu \"$word\" vào sổ tay",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Chạm vào thư mục để lưu hoặc bỏ chọn, hoặc tạo thư mục mới ngay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Quick "+ Tạo thư mục mới" right at the top
            if (!showCreateForm) {
                FilledTonalButton(
                    onClick = {
                        haptics.lightTap()
                        showCreateForm = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("+ Tạo thư mục mới", fontWeight = FontWeight.SemiBold)
                }
            }

            AnimatedVisibility(visible = showCreateForm) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            label = { Text("Tên thư mục mới") },
                            placeholder = { Text("VD: Động vật, Giao tiếp, IELTS...") }
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Màu thẻ:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FOLDER_COLOR_PALETTE.forEach { hex ->
                                val swatch = parseFolderColor(hex, MaterialTheme.colorScheme.primary)
                                val isSelected = hex == selectedColorHex
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(swatch)
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(
                                                    width = 2.5.dp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    shape = CircleShape
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clickable { selectedColorHex = hex }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    showCreateForm = false
                                    newFolderName = ""
                                }
                            ) {
                                Text("Hủy")
                            }
                            Button(
                                onClick = {
                                    val cleaned = newFolderName.trim()
                                    if (cleaned.isNotEmpty()) {
                                        haptics.confirm()
                                        onCreateAndSave(cleaned, selectedColorHex)
                                        newFolderName = ""
                                        showCreateForm = false
                                    }
                                },
                                enabled = newFolderName.isNotBlank(),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tạo & Lưu ngay")
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(folders, key = { it.folder.id }) { item ->
                    val isSaved = item.folder.id in savedFolderIds
                    val accent = parseFolderColor(item.folder.colorHex, MaterialTheme.colorScheme.primary)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                haptics.confirm()
                                onToggleFolder(item.folder)
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSaved) {
                            accent.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(accent.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = item.folder.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (item.dueCount > 0) {
                                            "${item.wordCount} từ • ${item.dueCount} đến hạn"
                                        } else {
                                            "${item.wordCount} từ"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (isSaved) {
                                    Icons.Default.CheckCircle
                                } else {
                                    Icons.Default.RadioButtonUnchecked
                                },
                                contentDescription = if (isSaved) "Đã lưu" else "Chưa lưu",
                                tint = if (isSaved) accent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (savedFolderIds.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                TextButton(
                    onClick = {
                        haptics.lightTap()
                        onRemoveAll()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkRemove,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Bỏ lưu khỏi sổ tay",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
