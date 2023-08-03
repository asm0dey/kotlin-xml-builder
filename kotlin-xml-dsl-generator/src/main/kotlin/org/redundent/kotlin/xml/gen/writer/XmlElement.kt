package org.redundent.kotlin.xml.gen.writer

import com.squareup.kotlinpoet.*
import com.sun.tools.xjc.model.CClassInfo
import com.sun.tools.xjc.model.CNonElement
import com.sun.tools.xjc.outline.Outline
import java.util.*

class XmlElement(
    val name: String,
    val type: CNonElement,
    private val tagName: String,
    private val documentation: String?,
    private val parent: CClassInfo? = null
) : Code {

    override fun write(codeWriter: CodeWriter) {
        val rootElement = parent == null
        with(codeWriter) {
            writeKotlinDoc(documentation)

            val funLine = "fun ${if (!rootElement) "`${parent!!.shortName}`." else ""}`$name`"

            if (type is CClassInfo && type.hasOptionalAttributes) {
                writeln("@${JvmOverloads::class.simpleName}")
            }

            write(funLine)

            if (type is CClassInfo) {
                val blockParamType =
                    "${((type.parent() as? CClassInfo)?.let { "`${it.shortName}`." } ?: "")}`${type.shortName}`"

                writeln(
                    "(${type.attributesAsParameters(funLine.length + (currentIndex * 4) + 1)}__block__: $blockParamType.() -> Unit)${if (rootElement) ": `${type.shortName}`" else ""} {",
                    false
                )
                indent()
                writeln("val `$tagName` = `${type.shortName}`(${if (type.parent() is CClassInfo) "" else "\"$tagName\""})")
                if (type.allAttributes.isNotEmpty()) {
                    writeln("`$tagName`.apply {")
                    indent()
                    for (attr in type.allAttributes) {
                        val attrName = attr.xmlName.localPart
                        if (!attr.isRequired) {
                            writeln("if (`$attrName` != null) {")
                            writeln("\tthis.`$attrName` = `$attrName`")
                            writeln("}")
                        } else {
                            writeln("this.`$attrName` = `$attrName`")
                        }
                    }
                    dedent()
                    writeln("}")
                }

                writeln("`$tagName`.apply(__block__)")
                if (rootElement) {
                    writeln("return `$tagName`")
                } else {
                    writeln("this.addNode(`$tagName`)")
                }

                dedent()
                writeln("}\n")
            } else {
                val t = CodeWriter.mapType(type.type.fullName())
                writeln("(value: $t) {", false)
                indent()
                writeln("\"$tagName\"(value${if (t != String::class.qualifiedName) ".toString()" else ""})")
                dedent()
                writeln("}\n")
            }
        }
    }

    fun toFunSpec(outline: Outline): FunSpec {
        val rootElement = parent == null
        val functionBuilder = FunSpec.builder(name)
        return `fun`(name) {
            if (documentation != null) kdoc(documentation.trimIndent())
            if (!rootElement) receiver(ClassName.bestGuess(parent!!.shortName))
            if (type is CClassInfo && type.hasOptionalAttributes) addAnnotation(JvmOverloads::class)
            if (type !is CClassInfo) {
                val t = mapType(type.type.fullName())
                addParameter("value", t)
                val toString = if (t != String::class.asTypeName()) ".toString()" else ""
                addStatement("%S(%L$toString)", tagName, "value")
                return@`fun`
            }
            val sortedAttributes =
                type.allAttributes
                    .distinctBy { it.xmlName.localPart }
                    .sortedWith(compareBy({ !it.isRequired }, { it.xmlName.localPart }))
            for (attr in sortedAttributes) {
                val paramName = attr.xmlName.localPart
                if (functionBuilder.parameters.any { it.name == paramName }) continue
                val field = outline.getField(attr)
                val paramType = mapType(field.rawType.fullName()).copy(nullable = !attr.isRequired)
                parameter(paramName, paramType) {
                    if (!attr.isRequired) defaultValue("%L", null)
                }
            }
            val lambdaType = LambdaTypeName.get(ClassName.bestGuess(type.fullName()), listOf(), Unit::class.asTypeName())
            parameter("block", lambdaType) { defaultValue("{}") }
            if (type.parent() is CClassInfo)
                addStatement("val %N = %T()", tagName, ClassName.bestGuess(type.fullName()))
            else
                addStatement("val %N = %T(%S)", tagName, ClassName.bestGuess(type.fullName()), tagName)
            if (type.allAttributes.isNotEmpty()) {
                addCode(applyParameters(type))
            }
            addStatement("%N.block()", tagName)

            if (rootElement) {
                returns(ClassName.bestGuess(type.fullName()))
                addStatement("return %N", tagName)
            } else {
                addStatement("this.addNode(%L)", tagName)
            }
        }
    }

    private fun applyParameters(type: CClassInfo) = buildCodeBlock {
        controlFlow("%N.apply", tagName) {
            for (attr in type.allAttributes.distinctBy { it.xmlName.localPart }) {
                val attrName = attr.xmlName.localPart
                if (!attr.isRequired) {
                    addStatement(
                        "if (%1L != null) this.%2L = %1L",
                        attrName,
                        if (attrName == "version") "xVersion" else attrName
                    )
                } else {
                    addStatement("this.%1L = %1L", attrName)
                }
            }
        }
    }
}

fun CodeBlock.Builder.controlFlow(controlFlow: String, vararg args: Any?, block: CodeBlock.Builder.() -> Unit) {
    beginControlFlow(controlFlow, *args)
    block()
    endControlFlow()
}

private fun FunSpec.Builder.parameter(name: String, type: TypeName, block: ParameterSpec.Builder.() -> Unit) {
    addParameter(ParameterSpec.builder(name, type).apply(block).build())
}

private fun FunSpec.Builder.kdoc(s: String) {
    addKdoc(CodeBlock.of("%L", s))
}

fun `fun`(funName: String, block: FunSpec.Builder.() -> Unit): FunSpec {
    return FunSpec.builder(funName).apply(block).build()
}

fun mapType(type: String): TypeName {
    return when (type) {
        "int",
        java.lang.Integer::class.java.name,
        java.math.BigInteger::class.java.name -> Int::class.asTypeName()

        "long",
        java.lang.Long::class.java.name -> Long::class.asTypeName()

        "boolean",
        java.lang.Boolean::class.java.name -> Boolean::class.asTypeName()

        "double",
        java.lang.Double::class.java.name -> Double::class.asTypeName()

        "float",
        java.lang.Float::class.java.name -> Float::class.asTypeName()

        java.lang.String::class.java.name,
        javax.xml.namespace.QName::class.java.name -> String::class.asTypeName()

        "byte",
        java.lang.Byte::class.java.name -> Byte::class.asTypeName()

        "short",
        java.lang.Short::class.java.name -> Short::class.asTypeName()

        "byte[]" -> ByteArray::class.asTypeName()
        javax.xml.datatype.XMLGregorianCalendar::class.java.name -> Date::class.asTypeName()
        else -> ClassName.bestGuess(type)
    }
}

