package com.serrano.dictproject.customui.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.serrano.dictproject.customui.WindowInfo
import com.serrano.dictproject.customui.button.TextWithEditButton
import com.serrano.dictproject.customui.dropdown.DueDropDown
import com.serrano.dictproject.customui.dropdown.PriorityDropDown
import com.serrano.dictproject.customui.dropdown.StatusDropDown
import com.serrano.dictproject.customui.dropdown.TypeDropDown
import com.serrano.dictproject.utils.FileUtils
import com.serrano.dictproject.utils.SubtaskState

/**
 * Container used for received subtasks
 *
 * @param[currentUserId] The id of user to determine who the user. User created the subtask is only the user can delete it.
 * @param[windowInfo] Used for responsive/adaptive content
 * @param[task] Subtask information
 * @param[navigateToProfile] Navigate to user profile when the user click the image of subtask creator
 * @param[openViewDialog] Open view assignee dialog when the user click the assignees of subtask
 * @param[onDescriptionClick] Open dialog to edit description of subtask
 * @param[onStatusClick] Open dialog to edit status of subtask
 * @param[onAssigneeClick] Open dialog to edit assignee of subtask
 * @param[onPriorityClick] Open dialog to edit priority of subtask
 * @param[onDueClick] Open dialog to edit due date of subtask
 * @param[onTypeClick] Open dialog to edit type of subtask
 * @param[onDeleteClick] Delete the subtask on the server
 */
@Composable
fun SubtaskBox(
    currentUserId: Int,
    windowInfo: WindowInfo,
    task: SubtaskState,
    navigateToProfile: (Int) -> Unit,
    openViewDialog: () -> Unit,
    onDescriptionClick: () -> Unit,
    onStatusClick: () -> Unit,
    onAssigneeClick: () -> Unit,
    onPriorityClick: () -> Unit,
    onDueClick: () -> Unit,
    onTypeClick: () -> Unit,
    onDeleteClick: (Int) -> Unit
) {
    val assigneesComposable: @Composable RowScope.() -> Unit = {
        Row {
            Row(horizontalArrangement = Arrangement.spacedBy((-25).dp)) {
                task.assignees.forEach {
                    IconButton(onClick = openViewDialog) {
                        Icon(
                            bitmap = FileUtils.encodedStringToImage(it.image),
                            contentDescription = null,
                            tint = Color.Unspecified
                        )
                    }
                }
            }
            IconButton(
                onClick = onAssigneeClick,
                modifier = Modifier
                    .size(25.dp)
                    .padding(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(25.dp)
                )
            }
        }
    }

    val deleteIcon: @Composable RowScope.() -> Unit = {
        if (task.creator.id == currentUserId) {
            IconButton(
                onClick = { onDeleteClick(task.subtaskId) },
                enabled = task.deleteIconEnabled
            ) {
                if (task.deleteIconEnabled) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null
                    )
                } else {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(25.dp)
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .padding(5.dp)
            .fillMaxWidth()
            .border(
                BorderStroke(2.dp, MaterialTheme.colorScheme.onPrimaryContainer),
                MaterialTheme.shapes.small
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { navigateToProfile(task.creator.id) },
                modifier = Modifier
                    .size(100.dp)
                    .padding(5.dp)
            ) {
                Icon(
                    bitmap = FileUtils.encodedStringToImage(task.creator.image),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(100.dp)
                )
            }
            TextWithEditButton(
                text = task.description,
                onEditButtonClick = onDescriptionClick,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                isOneLine = false
            )
        }
        when (windowInfo.screenWidthInfo) {
            is WindowInfo.WindowType.Compact -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(5.dp)
                ) {
                    DueDropDown(date = task.due, onClick = onDueClick)
                    StatusDropDown(text = task.status, onClick = onStatusClick)
                    PriorityDropDown(text = task.priority, onClick = onPriorityClick)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(5.dp)
                    ) {
                        assigneesComposable()
                        TypeDropDown(text = task.type, onClick = onTypeClick)
                    }
                    deleteIcon()
                }
            }
            is WindowInfo.WindowType.Medium, is WindowInfo.WindowType.Expanded -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(5.dp)
                    ) {
                        assigneesComposable()
                        StatusDropDown(text = task.status, onClick = onStatusClick)
                        PriorityDropDown(text = task.priority, onClick = onPriorityClick)
                        DueDropDown(date = task.due, onClick = onDueClick)
                        TypeDropDown(text = task.type, onClick = onTypeClick)
                    }
                    deleteIcon()
                }
            }
        }
    }
}