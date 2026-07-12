package com.example.aethermesh.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aethermesh.data.MeshNode
import com.example.aethermesh.ui.components.aetherTextFieldColors

@Composable
fun RenameNodeDialog(
    node: MeshNode,
    connectedNodeId: Long?,
    appLanguage: String = "English",
    onRename: (nodeId: Long, longName: String, shortName: String, password: String) -> Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val spanish = appLanguage == "Spanish"
    var longName by remember(node.nodeId) { mutableStateOf(node.name) }
    var shortName by remember(node.nodeId) {
        mutableStateOf(node.shortName.ifEmpty { getShortName(node.name, node.nodeId) })
    }
    var adminPassword by remember(node.nodeId) { mutableStateOf("") }
    val isRemote = node.nodeId != connectedNodeId

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Rename Node", appLanguage), color = TextLight, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(t("Long Name (max 16 chars)", appLanguage), color = TextMuted, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                TextField(
                    value = longName,
                    onValueChange = { if (it.length <= 16) longName = it },
                    singleLine = true,
                    colors = aetherTextFieldColors(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(t("Short Name (max 4 chars)", appLanguage), color = TextMuted, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                TextField(
                    value = shortName,
                    onValueChange = { if (it.length <= 4) shortName = it.uppercase() },
                    singleLine = true,
                    colors = aetherTextFieldColors(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isRemote) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (spanish)
                            "Contraseña del nodo (para guardar en el mesh)"
                        else
                            "Node admin password (to store on the mesh)",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = adminPassword,
                        onValueChange = { adminPassword = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = aetherTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        if (spanish)
                            "Sin contraseña el nombre solo queda en este teléfono."
                        else
                            "Without a password the name stays on this phone only.",
                        color = Color(0xFFFBBF24),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val persisted = onRename(
                        node.nodeId,
                        longName.trim(),
                        shortName.trim(),
                        adminPassword
                    )
                    if (!persisted && isRemote) {
                        android.widget.Toast.makeText(
                            context,
                            if (spanish)
                                "Nombre guardado solo en el teléfono. Conéctate al nodo o usa Config remota."
                            else
                                "Name saved on phone only. Connect to that node or use Remote Config.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                    onDismiss()
                }
            ) {
                Text(t("Save", appLanguage), color = AccentMint, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t("Cancel", appLanguage), color = TextMuted)
            }
        },
        containerColor = SurfaceDark
    )
}
