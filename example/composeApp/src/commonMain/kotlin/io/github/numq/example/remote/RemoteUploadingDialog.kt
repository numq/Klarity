package io.github.numq.example.remote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RemoteUploadingDialog(isVisible: Boolean, done: (String) -> Unit, close: () -> Unit) {
    val (address, setAddress) = remember(isVisible) { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible = isVisible, enter = fadeIn(), exit = fadeOut()) {
            Card {
                Column(
                    modifier = Modifier.fillMaxWidth(.75f).padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(space = 8.dp, alignment = Alignment.CenterVertically)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Input remote media address",
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = {
                            close()
                            setAddress("")
                        }) {
                            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    OutlinedTextField(
                        value = address,
                        onValueChange = setAddress,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(
                                    space = 8.dp, alignment = Alignment.CenterHorizontally
                                )
                            ) {
                                IconButton(onClick = {
                                    setAddress("")
                                }, enabled = address.isNotBlank()) {
                                    Icon(Icons.Default.Cancel, null, tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = {
                                    done(address)
                                    setAddress("")
                                }, enabled = address.isNotBlank()) {
                                    Icon(Icons.Default.Done, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        },
                        maxLines = 10
                    )
                }
            }
        }
    }
}