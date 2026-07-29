package com.pelonot.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.viewinterop.AndroidView
import com.pelonot.data.local.entity.UserEntity

@Composable
fun ProfileSelectorScreen(
    users: List<UserEntity>,
    onProfileSelected: (UserEntity) -> Unit,
    onGuestSelected: () -> Unit,
    onCreateProfile: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Select Profile",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f)
        ) {
            items(users) { user ->
                ProfileCard(
                    user = user,
                    onClick = { onProfileSelected(user) }
                )
            }

            // Guest mode card
            item {
                ProfileCard(
                    user = UserEntity(
                        localUserId = -1,
                        name = "Guest",
                        weightKg = 70.0,
                        ftpWatts = 200
                    ),
                    onClick = onGuestSelected
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onCreateProfile,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Create New Profile")
        }
    }
}

@Composable
private fun ProfileCard(
    user: UserEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .aspectRatio(1f),
        onClick = onClick
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}