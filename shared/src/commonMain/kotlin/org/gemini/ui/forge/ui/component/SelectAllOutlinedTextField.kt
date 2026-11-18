package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.extend.onEnterConfirm
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 封装的全局输入框组件：
 * 1. 支持在通过键盘 (Tab) 获得焦点时自动全选内容。
 * 2. 如果是通过鼠标/触摸点击获得焦点，则保持系统原生行为（光标停留在点击处），避免出现先全选再跳光标的闪烁问题。
 * 3. 组件尺寸、间距与字号 100% 由 [LocalAppSpacing] 与 [MaterialTheme.typography] 动态接管，
 *    自动适配桌面紧凑模式与移动端触控模式，绝不写死固定尺度。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectAllOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    shape: Shape = MaterialTheme.shapes.small,
    textStyle: TextStyle? = null,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    singleLine: Boolean = maxLines == 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    isError: Boolean = false,
    contentPadding: PaddingValues? = null,
    streamlined: Boolean = (label == null),
    onEnterConfirm: (() -> Unit)? = null,
    onBlurConfirm: (() -> Unit)? = null
) {
    val spacing = LocalAppSpacing.current
    var textFieldValue by remember { mutableStateOf(TextFieldValue(text = value)) }
    var isFocused by remember { mutableStateOf(false) }
    var isPointerDown by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (textFieldValue.text != value) {
            textFieldValue = textFieldValue.copy(text = value)
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors()

    val baseTypography = if (streamlined && label == null) {
        if (spacing.isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
    } else {
        MaterialTheme.typography.bodyMedium
    }
    val effectiveStyle = (textStyle ?: baseTypography).merge(
        TextStyle(color = MaterialTheme.colorScheme.onSurface)
    )
    val cursorColor = MaterialTheme.colorScheme.primary

    val heightModifier = if (streamlined && label == null) {
        if (singleLine) Modifier.height(spacing.inspectorFieldHeight)
        else Modifier.defaultMinSize(minHeight = spacing.inspectorFieldHeight)
    } else {
        Modifier
    }

    BasicTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            textFieldValue = newValue
            if (newValue.text != value) {
                onValueChange(newValue.text)
            }
        },
        modifier = modifier
            .then(heightModifier)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        when (event.type) {
                            PointerEventType.Press -> isPointerDown = true
                            PointerEventType.Release -> isPointerDown = false
                        }
                    }
                }
            }
            .onFocusChanged { focusState ->
                if (focusState.isFocused && !isFocused) {
                    if (!isPointerDown) {
                        textFieldValue = textFieldValue.copy(
                            selection = TextRange(0, textFieldValue.text.length)
                        )
                    }
                } else if (!focusState.isFocused && isFocused) {
                    onBlurConfirm?.invoke()
                }
                isFocused = focusState.isFocused
            }
            .onEnterConfirm(enabled = onEnterConfirm != null) {
                onEnterConfirm?.invoke()
            },
        enabled = enabled,
        readOnly = readOnly,
        textStyle = effectiveStyle,
        cursorBrush = SolidColor(cursorColor),
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        visualTransformation = visualTransformation,
        interactionSource = interactionSource,
        decorationBox = @Composable { innerTextField ->
            if (streamlined && label == null) {
                val borderShape = RoundedCornerShape(spacing.inspectorFieldCornerRadius)
                val borderColor = when {
                    isError -> MaterialTheme.colorScheme.error
                    isFocused -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.inspectorControlGap),
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(borderShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f))
                        .border(if (isFocused) 1.dp else 0.8.dp, borderColor, borderShape)
                        .padding(
                            horizontal = spacing.inspectorFieldPaddingHorizontal,
                            vertical = if (singleLine) 0.dp else spacing.extraSmall
                        )
                ) {
                    if (leadingIcon != null) {
                        leadingIcon()
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (textFieldValue.text.isEmpty() && placeholder != null) {
                            placeholder()
                        }
                        innerTextField()
                    }
                    if (trailingIcon != null) {
                        trailingIcon()
                    }
                }
            } else {
                OutlinedTextFieldDefaults.DecorationBox(
                    value = textFieldValue.text,
                    visualTransformation = visualTransformation,
                    innerTextField = innerTextField,
                    placeholder = placeholder,
                    label = label,
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon,
                    singleLine = singleLine,
                    enabled = enabled,
                    isError = isError,
                    interactionSource = interactionSource,
                    colors = colors,
                    contentPadding = contentPadding ?: OutlinedTextFieldDefaults.contentPadding(
                        start = spacing.textFieldPaddingHorizontal,
                        top = spacing.textFieldPaddingVertical,
                        end = spacing.textFieldPaddingHorizontal,
                        bottom = spacing.textFieldPaddingVertical
                    ),
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = enabled,
                            isError = false,
                            interactionSource = interactionSource,
                            colors = colors,
                            shape = shape
                        )
                    }
                )
            }
        }
    )
}
