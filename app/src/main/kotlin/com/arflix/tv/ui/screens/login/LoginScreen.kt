package com.arflix.tv.ui.screens.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.arflix.tv.R
import com.arflix.tv.ui.components.*
import com.arflix.tv.ui.theme.*

/**
 * Xtream Codes login screen - Optimized for TV
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedPortalIndex by remember { mutableIntStateOf(0) }
    var portalSelectionInitialized by remember { mutableStateOf(false) }
    var focusedField by remember { mutableStateOf("service") }

    val serviceFocusRequester = remember { FocusRequester() }
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val buttonFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val selectedPortal = uiState.portals.getOrNull(selectedPortalIndex)

    val logoAccentBrush = remember {
        Brush.horizontalGradient(
            listOf(
                Color(0xFF00D7E8),
                Color(0xFF00F0B5),
                Color(0xFF6AF24A)
            )
        )
    }
    val logoLineBrush = remember {
        Brush.horizontalGradient(
            listOf(
                Color.Transparent,
                Color(0xFF00D7E8).copy(alpha = 0.72f),
                Color(0xFF00F0B5).copy(alpha = 0.72f),
                Color.Transparent
            )
        )
    }

    // Handle successful login
    LaunchedEffect(uiState.loginReady) {
        if (uiState.loginReady) {
            viewModel.onLoginNavigationHandled()
            onLoginSuccess()
        }
    }

    // Request initial focus
    LaunchedEffect(Unit) {
        serviceFocusRequester.requestFocus()
    }

    LaunchedEffect(uiState.portals) {
        if (uiState.portals.isEmpty()) {
            selectedPortalIndex = 0
            portalSelectionInitialized = false
        } else if (!portalSelectionInitialized) {
            selectedPortalIndex = uiState.portals.indexOfFirst { it.id != 0 }.takeIf { it >= 0 } ?: 0
            portalSelectionInitialized = true
        } else if (selectedPortalIndex !in uiState.portals.indices) {
            selectedPortalIndex = 0
        }
    }

    // Handle keyboard navigation
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionDown -> {
                            when (focusedField) {
                                "service" -> {
                                    usernameFocusRequester.requestFocus()
                                    true
                                }
                                "username" -> {
                                    passwordFocusRequester.requestFocus()
                                    true
                                }
                                "password" -> {
                                    buttonFocusRequester.requestFocus()
                                    true
                                }
                                else -> false
                            }
                        }
                        Key.DirectionUp -> {
                            when (focusedField) {
                                "username" -> {
                                    serviceFocusRequester.requestFocus()
                                    true
                                }
                                "password" -> {
                                    usernameFocusRequester.requestFocus()
                                    true
                                }
                                "button" -> {
                                    passwordFocusRequester.requestFocus()
                                    true
                                }
                                else -> false
                            }
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compactHeight = maxHeight < 620.dp
            val logoWidth = if (compactHeight) 184.dp else 220.dp
            val logoHeight = if (compactHeight) 138.dp else 166.dp
            val logoLineWidth = if (compactHeight) 204.dp else 236.dp
            val pageVerticalPadding = if (compactHeight) 18.dp else 28.dp
            val logoFormGap = if (compactHeight) 10.dp else 14.dp
            val formWidth = if (compactHeight) 390.dp else 430.dp
            val formHorizontalPadding = if (compactHeight) 22.dp else 24.dp
            val formVerticalPadding = if (compactHeight) 16.dp else 20.dp
            val headingGap = if (compactHeight) 9.dp else 12.dp
            val fieldGap = if (compactHeight) 10.dp else 12.dp
            val buttonTopGap = if (compactHeight) 12.dp else 16.dp
            val fieldHeight = if (compactHeight) 48.dp else 58.dp
            val buttonHeight = if (compactHeight) 46.dp else 50.dp
            val fieldFontSize = if (compactHeight) 13.sp else 15.sp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp, vertical = pageVerticalPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.arvio_loading_logo),
                    contentDescription = "ARVIO",
                    modifier = Modifier
                        .width(logoWidth)
                        .height(logoHeight)
                )

                Box(
                    modifier = Modifier
                        .width(logoLineWidth)
                        .height(1.dp)
                        .background(logoLineBrush)
                )

                Spacer(modifier = Modifier.height(logoFormGap))

                Column(
                    modifier = Modifier
                        .width(formWidth)
                        .shadow(18.dp, RoundedCornerShape(12.dp), clip = false)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.78f))
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.16f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = formHorizontalPadding, vertical = formVerticalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                Box(
                    modifier = Modifier
                        .width(58.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(logoAccentBrush)
                )

                Spacer(modifier = Modifier.height(headingGap))

                Text(
                    text = "XTREAM CODES",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                    color = TextPrimary
                )

                Text(
                    text = "Select service and sign in",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Spacer(modifier = Modifier.height(buttonTopGap))

                // Error message
                if (uiState.error != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ErrorRed.copy(alpha = 0.14f))
                            .border(1.dp, ErrorRed.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = uiState.error!!,
                            fontSize = 13.sp,
                            color = Color(0xFFEF4444)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Service field
                GradientButton(
                    onClick = {
                        if (uiState.portals.isNotEmpty()) {
                            selectedPortalIndex = (selectedPortalIndex + 1) % uiState.portals.size
                        }
                    },
                    text = when {
                        uiState.isLoadingPortals -> "Loading services..."
                        selectedPortal != null -> selectedPortal.name.ifBlank { selectedPortal.url }
                        else -> "No services available"
                    },
                    isPrimary = false,
                    isFocused = focusedField == "service",
                    enabled = !uiState.isLoading && uiState.portals.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(buttonHeight)
                        .focusRequester(serviceFocusRequester)
                        .onFocusChanged { if (it.isFocused) focusedField = "service" }
                )

                Spacer(modifier = Modifier.height(fieldGap))

                // Username field
                PremiumTextField(
                    value = username,
                    onValueChange = { username = it },
                    placeholder = "Xtream Username",
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                    keyboardActions = KeyboardActions(
                        onNext = { passwordFocusRequester.requestFocus() }
                    ),
                    onRequestKeyboard = { keyboardController?.show() },
                    isFocused = focusedField == "username",
                    fieldHeight = fieldHeight,
                    fontSize = fieldFontSize,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(usernameFocusRequester)
                        .onFocusChanged {
                            if (it.isFocused) {
                                focusedField = "username"
                            }
                        }
                )

                Spacer(modifier = Modifier.height(fieldGap))

                // Password field
                PremiumTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "Xtream Password",
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            buttonFocusRequester.requestFocus()
                        }
                    ),
                    onRequestKeyboard = { keyboardController?.show() },
                    isFocused = focusedField == "password",
                    fieldHeight = fieldHeight,
                    fontSize = fieldFontSize,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(passwordFocusRequester)
                        .onFocusChanged {
                            if (it.isFocused) {
                                focusedField = "password"
                            }
                        }
                )

                Spacer(modifier = Modifier.height(buttonTopGap))

                // Sign In button
                GradientButton(
                    onClick = {
                        viewModel.signIn(username, password, selectedPortal?.url.orEmpty())
                    },
                    text = if (uiState.isLoading) "Connecting..." else "Connect",
                    isPrimary = true,
                    isFocused = focusedField == "button",
                    enabled = !uiState.isLoading && selectedPortal != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(buttonHeight)
                        .focusRequester(buttonFocusRequester)
                        .onFocusChanged { if (it.isFocused) focusedField = "button" }
                )

                // Loading indicator
                if (uiState.isLoading) {
                    Spacer(modifier = Modifier.height(20.dp))
                    SimpleLoadingDots(
                        dotCount = 3,
                        dotSize = 6.dp,
                        color = Color(0xFF00D7E8)
                    )
                }
            }
        }
    }
}
}
/**
 * Premium styled text field with gradient border on focus
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PremiumTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onRequestKeyboard: () -> Unit = {},
    isPassword: Boolean = false,
    isFocused: Boolean = false,
    fieldHeight: androidx.compose.ui.unit.Dp = 58.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 15.sp,
    modifier: Modifier = Modifier
) {
    val backgroundColor = Color(0xFF15151A)

    Box(
        modifier = modifier
            .height(fieldHeight)
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (isFocused) {
                    Modifier.background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF00D7E8), Color(0xFF00F0B5), Color(0xFF6AF24A))
                        ),
                        RoundedCornerShape(14.dp)
                    )
                } else {
                    Modifier.background(
                        BorderMedium.copy(alpha = 0.55f),
                        RoundedCornerShape(14.dp)
                    )
                }
            )
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                onRequestKeyboard()
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                },
            textStyle = TextStyle(
                fontSize = fontSize,
                color = TextPrimary,
                fontWeight = FontWeight.Normal
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction
            ),
            keyboardActions = keyboardActions,
            singleLine = true,
            cursorBrush = SolidColor(Color(0xFF00D7E8)),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = fontSize,
                            color = TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}
/**
 * Gradient button with premium styling
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GradientButton(
    onClick: () -> Unit,
    text: String,
    isPrimary: Boolean,
    isFocused: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val focusedBackground = Color(0xFFE9FFFB)
    val focusedText = ArcticBlack
    val noScale = ButtonDefaults.scale(1f, 1f, 1f, 1f, 1f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isPrimary) {
                    if (isFocused) {
                        Modifier.background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF00D7E8), Color(0xFF00F0B5), Color(0xFF6AF24A))
                            )
                        )
                    } else {
                        Modifier
                            .background(
                                Color(0xFF050607),
                                RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(12.dp)
                            )
                    }
                } else {
                    Modifier
                        .background(
                            if (isFocused) focusedBackground else Color(0xFF111216),
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(listOf(BorderLight, BorderLight)),
                            shape = RoundedCornerShape(12.dp)
                        )
                }
            ),
        contentAlignment = if (isPrimary) Alignment.Center else Alignment.CenterStart
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxSize(),
            colors = ButtonDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent
            ),
            scale = noScale,
            shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
        ) {}

        Text(
            text = text,
            modifier = Modifier.padding(horizontal = if (isPrimary) 0.dp else 18.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isFocused) focusedText else if (isPrimary) TextPrimary else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
