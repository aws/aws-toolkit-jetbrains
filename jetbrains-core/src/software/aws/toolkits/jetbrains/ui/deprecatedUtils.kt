import com.intellij.ui.UIBundle
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.CellBuilder
import com.intellij.ui.layout.Row
import com.intellij.ui.layout.toBinding
import kotlin.reflect.KMutableProperty0

fun Row.intTextField(prop: KMutableProperty0<Int>, columns: Int? = null, range: IntRange? = null): CellBuilder<JBTextField> {
    val binding = prop.toBinding()
    return textField(
        { binding.get().toString() },
        { value -> value.toIntOrNull()?.let { intValue -> binding.set(range?.let { intValue.coerceIn(it.first, it.last) } ?: intValue) } },
        columns
    ).withValidationOnInput {
        val value = it.text.toIntOrNull()
        when {
            value == null -> error(UIBundle.message("please.enter.a.number"))
            range != null && value !in range -> error(UIBundle.message("please.enter.a.number.from.0.to.1", range.first, range.last))
            else -> null
        }
    }
}
