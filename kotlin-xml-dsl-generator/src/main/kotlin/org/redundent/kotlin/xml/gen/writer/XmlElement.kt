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
        if (documentation != null)
            functionBuilder.addKdoc("%L", documentation.trimIndent())
        if (!rootElement) functionBuilder.receiver(ClassName.bestGuess(parent!!.shortName))
        if (type is CClassInfo && type.hasOptionalAttributes) functionBuilder.addAnnotation(JvmOverloads::class)
        if (type is CClassInfo) {
            val blockParamType = type.fullName()
            val sortedS = type.allAttributes.sortedWith(compareBy({ !it.isRequired }, { it.xmlName.localPart }))
            for (sorted in sortedS) {
                if (functionBuilder.parameters.any { it.name == sorted.xmlName.localPart }) continue
                val field = outline.getField(sorted)
                val parameterSpec = ParameterSpec.builder(
                    sorted.xmlName.localPart,
                    mapType(field.rawType.fullName()).copy(nullable = !sorted.isRequired)
                )
                if (!sorted.isRequired) parameterSpec.defaultValue("%L", null)
                functionBuilder.addParameter(parameterSpec.build())
            }
            val lambda = LambdaTypeName.get(ClassName.bestGuess(blockParamType), listOf(), Unit::class.asTypeName())
            functionBuilder.addParameter(ParameterSpec.builder("block", lambda).defaultValue("{}").build())
            if (type.parent() is CClassInfo) functionBuilder.addStatement("val %N = %T()", tagName, ClassName.bestGuess(type.fullName()))
            else functionBuilder.addStatement("val %N = %T(%S)", tagName, ClassName.bestGuess(type.fullName()), tagName)
            if (type.allAttributes.isNotEmpty()) {
                functionBuilder.addCode(buildCodeBlock {

                    this.beginControlFlow("%N.apply", tagName)
                    for (attr in type.allAttributes.distinctBy { it.xmlName.localPart }) {
                        val attrName = attr.xmlName.localPart
                        if (!attr.isRequired) {
                            this.addStatement(
                                "if (%1L != null) this.%2L = %1L",
                                attrName,
                                if (attrName == "version") "xVersion" else attrName
                            )
                        } else {
                            this.addStatement("this.%1L = %1L", attrName)
                        }
                    }
                    this.endControlFlow()
                })
            }
            functionBuilder.addStatement("%N.block()", tagName)

            if (rootElement) {
                functionBuilder.returns(ClassName.bestGuess(type.fullName()))
                functionBuilder.addStatement("return %N", tagName)
            } else {
                functionBuilder.addStatement("this.addNode(%L)", tagName)
            }

        } else {
            val t = mapType(type.type.fullName())
            functionBuilder.addParameter("value", t)
            functionBuilder.addStatement(
                "%S(%L${if (t != String::class.asTypeName()) ".toString()" else ""})",
                tagName,
                "value",
            )
        }
        return functionBuilder.build()
    }
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

