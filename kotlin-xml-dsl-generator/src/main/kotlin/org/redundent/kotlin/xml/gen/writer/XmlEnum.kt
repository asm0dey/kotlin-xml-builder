package org.redundent.kotlin.xml.gen.writer

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.sun.tools.xjc.outline.EnumOutline

class XmlEnum(val enum: EnumOutline) : Code {
    override fun write(codeWriter: CodeWriter) {
        with(codeWriter) {
            writeKotlinDoc(enum.target.documentation)
            writeln("enum class `${enum.target.shortName}` {")
            indent()

            writeln(enum.constants.joinToString(",\n\t") { "`${it.target.lexicalValue}`" })
            dedent()
            writeln("}\n")
        }
    }

    fun toTypeSpec(): TypeSpec {
        val builder = TypeSpec.enumBuilder(enum.target.shortName)
        if (enum.target.documentation != null)
            builder.addKdoc(CodeBlock.of("%L", enum.target.documentation!!.trimIndent()))
        val hasSlashes = enum.constants.any { it.target.lexicalValue.contains('/') }
        if (hasSlashes) builder.primaryConstructor(
            FunSpec.constructorBuilder().addParameter("str", String::class).build()
        )
        enum.constants.forEach {
            if (!hasSlashes)
                builder.addEnumConstant(it.target.lexicalValue)
            else {
                builder
                    .addEnumConstant(
                        it.target.lexicalValue.substringAfterLast('/').uppercase(),
                        TypeSpec.anonymousClassBuilder()
                            .addSuperclassConstructorParameter("%S", it.target.lexicalValue)
                            .build()
                    )
            }
        }
        if (hasSlashes) {
            builder.addProperty(
                PropertySpec.builder("str", String::class)
                    .initializer("str")
                    .build()
            )
        }
        return builder.build()
    }
}