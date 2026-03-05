package com.srtgo.app.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srtgo.app.ui.theme.Green
import com.srtgo.app.ui.theme.KtxPurple
import com.srtgo.app.ui.theme.Red
import com.srtgo.app.ui.theme.SrtOrange

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Section 1: SRT
            item {
                CredentialSection(
                    title = "SRT 로그인",
                    icon = { Icon(Icons.Default.Train, contentDescription = null, tint = SrtOrange) },
                    idLabel = "SRT 회원번호/이메일/전화번호",
                    idValue = uiState.srtId,
                    onIdChange = viewModel::updateSrtId,
                    pwValue = uiState.srtPw,
                    onPwChange = viewModel::updateSrtPw,
                    loginStatus = uiState.srtLoginStatus,
                    onTest = viewModel::testSrtLogin,
                    onSave = viewModel::saveSrtCredential
                )
            }

            // Section 2: KTX
            item {
                CredentialSection(
                    title = "KTX 로그인",
                    icon = { Icon(Icons.Default.Train, contentDescription = null, tint = KtxPurple) },
                    idLabel = "KTX 회원번호/이메일/전화번호",
                    idValue = uiState.ktxId,
                    onIdChange = viewModel::updateKtxId,
                    pwValue = uiState.ktxPw,
                    onPwChange = viewModel::updateKtxPw,
                    loginStatus = uiState.ktxLoginStatus,
                    onTest = viewModel::testKtxLogin,
                    onSave = viewModel::saveKtxCredential
                )
            }

            // Section 3: Card
            item {
                CardInfoSection(
                    cardNumber = uiState.cardNumber,
                    onCardNumberChange = viewModel::updateCardNumber,
                    cardPassword = uiState.cardPassword,
                    onCardPasswordChange = viewModel::updateCardPassword,
                    cardBirthday = uiState.cardBirthday,
                    onCardBirthdayChange = viewModel::updateCardBirthday,
                    cardExpire = uiState.cardExpire,
                    onCardExpireChange = viewModel::updateCardExpire,
                    cardSaved = uiState.cardSaved,
                    onSave = viewModel::saveCard
                )
            }

            // Section 4: Telegram
            item {
                TelegramSection(
                    tgToken = uiState.tgToken,
                    onTgTokenChange = viewModel::updateTgToken,
                    tgChatId = uiState.tgChatId,
                    onTgChatIdChange = viewModel::updateTgChatId,
                    tgStatus = uiState.tgStatus,
                    onTest = viewModel::testTelegram,
                    onSave = viewModel::saveTelegram
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun CredentialSection(
    title: String,
    icon: @Composable () -> Unit,
    idLabel: String,
    idValue: String,
    onIdChange: (String) -> Unit,
    pwValue: String,
    onPwChange: (String) -> Unit,
    loginStatus: LoginStatus,
    onTest: () -> Unit,
    onSave: () -> Unit
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = idValue,
                onValueChange = onIdChange,
                label = { Text(idLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = pwValue,
                onValueChange = onPwChange,
                label = { Text("비밀번호") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "비밀번호 숨기기" else "비밀번호 보기"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onTest,
                    enabled = loginStatus != LoginStatus.TESTING,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("로그인 테스트")
                }

                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("저장")
                }

                LoginStatusIcon(status = loginStatus)
            }
        }
    }
}

@Composable
private fun LoginStatusIcon(status: LoginStatus) {
    when (status) {
        LoginStatus.NONE -> {}
        LoginStatus.TESTING -> {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        }
        LoginStatus.SUCCESS -> {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "성공",
                tint = Green,
                modifier = Modifier.size(24.dp)
            )
        }
        LoginStatus.FAILED -> {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "실패",
                tint = Red,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun CardInfoSection(
    cardNumber: String,
    onCardNumberChange: (String) -> Unit,
    cardPassword: String,
    onCardPasswordChange: (String) -> Unit,
    cardBirthday: String,
    onCardBirthdayChange: (String) -> Unit,
    cardExpire: String,
    onCardExpireChange: (String) -> Unit,
    cardSaved: Boolean,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CreditCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "카드 정보",
                    style = MaterialTheme.typography.titleMedium
                )
                if (cardSaved) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "저장됨",
                        tint = Green,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = cardNumber,
                onValueChange = onCardNumberChange,
                label = { Text("카드번호") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = cardPassword,
                    onValueChange = onCardPasswordChange,
                    label = { Text("비밀번호 앞 2자리") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = cardBirthday,
                    onValueChange = onCardBirthdayChange,
                    label = { Text("생년월일 YYMMDD") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = cardExpire,
                onValueChange = onCardExpireChange,
                label = { Text("유효기간 YYMM") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("저장")
            }
        }
    }
}

@Composable
private fun TelegramSection(
    tgToken: String,
    onTgTokenChange: (String) -> Unit,
    tgChatId: String,
    onTgChatIdChange: (String) -> Unit,
    tgStatus: LoginStatus,
    onTest: () -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "텔레그램 알림",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = tgToken,
                onValueChange = onTgTokenChange,
                label = { Text("봇 토큰") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = tgChatId,
                onValueChange = onTgChatIdChange,
                label = { Text("채팅 ID") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onTest,
                    enabled = tgStatus != LoginStatus.TESTING,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("테스트 메시지 보내기")
                }

                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("저장")
                }

                LoginStatusIcon(status = tgStatus)
            }
        }
    }
}
