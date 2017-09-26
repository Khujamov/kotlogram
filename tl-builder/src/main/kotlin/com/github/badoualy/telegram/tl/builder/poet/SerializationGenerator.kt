package com.github.badoualy.telegram.tl.builder.poet

import com.github.badoualy.telegram.tl.builder.parser.*
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

internal fun serializeParameter(fieldName: String, fieldTlType: TLType): String = when (fieldTlType) {
    is TLTypeFunctional -> "writeTLMethod($fieldName, stream)"
    is TLTypeFlag -> "writeInt($fieldName, stream)"
    is TLTypeConditional -> {
        "writeIfMaskTrue($fieldName, ${fieldTlType.pow2Value()}) " +
                "{ ${serializeParameter("it",
                                        fieldTlType.realType)} }"
    }
    is TLTypeGeneric -> "writeTLVector($fieldName, stream)"
    is TLTypeRaw -> when (fieldTlType.name) {
        "int" -> "writeInt($fieldName, stream)"
        "long" -> "writeLong($fieldName, stream)"
        "double" -> "writeDouble($fieldName, stream)"
        "float" -> "writeFloat($fieldName, stream)"
        "string" -> "writeString($fieldName, stream)"
        "bytes" -> "writeTLBytes($fieldName, stream)"
        "Bool" -> "writeBoolean($fieldName, stream)"
        "true", "false" -> ""
        else -> "writeTLObject($fieldName, stream)"
    }
    else -> throw RuntimeException("Unsupported type $fieldTlType")
}

internal fun deserializeParameter(fieldTlType: TLType, fieldType: TypeName): String = when (fieldTlType) {
    is TLTypeFunctional -> "readTLMethod(stream, context) as TLMethod<T>"
    is TLTypeFlag -> "readInt(stream)"
    is TLTypeConditional -> {
        val realType = fieldTlType.realType

        if (realType is TLTypeRaw && arrayOf("true", "false").contains(realType.name)) {
            "isMaskTrue(${fieldTlType.pow2Value()})"
        } else {
            // TODO: maybe should default to null instead of false?
            val suffix = if (realType.name == "Bool") " ?: false" else ""
            "readIfMaskTrue(${fieldTlType.pow2Value()}) " +
                    "{ ${deserializeParameter(realType, fieldType)} }" + suffix
        }
    }
    is TLTypeGeneric -> when ((fieldTlType.parameters.first() as TLTypeRaw).name) {
        "int" -> "readTLIntVector(stream, context)"
        "long" -> "readTLLongVector(stream, context)"
        "string" -> "readTLStringVector(stream, context)"
        else -> "readTLVector(stream, context) as %1T"
    }
    is TLTypeRaw -> when (fieldTlType.name) {
        "int" -> "readInt(stream)"
        "long" -> "readLong(stream)"
        "double" -> "readDouble(stream)"
        "float" -> "readFloat(stream)"
        "string" -> "readTLString(stream)"
        "bytes" -> "readTLBytes(stream, context)"
        "Bool" -> "readTLBool(stream)"
        else -> {
            val className = (fieldType as ClassName).simpleName()
            val isAbstract = className.startsWith("TLAbs", true)
            if (isAbstract) {
                "readTLObject<%1T>(stream, context)"
            } else {
                "readTLObject<%1T>(stream, context, %1T::class.java, %1T.CONSTRUCTOR_ID)"
            }
        }
    }
    else -> throw RuntimeException("Unsupported type $fieldTlType")
}

internal fun computeSizeParameter(fieldName: String, fieldTlType: TLType): String = when (fieldTlType) {
    is TLTypeFunctional -> "$fieldName.computeSerializedSize()"
    is TLTypeFlag -> "SIZE_INT32"
    is TLTypeConditional -> {
        "addIfMaskTrue($fieldName, ${fieldTlType.pow2Value()}) " +
                "{ ${computeSizeParameter("it",
                                          fieldTlType.realType)} }"
    }
    is TLTypeGeneric -> "$fieldName.computeSerializedSize()"
    is TLTypeRaw -> {
        val name = fieldTlType.name
        when (name) {
            "int" -> "SIZE_INT32"
            "long" -> "SIZE_INT64"
            "double" -> "SIZE_DOUBLE"
            "float" -> "SIZE_DOUBLE"
            "string" -> "computeTLStringSerializedSize($fieldName)"
            "bytes" -> "computeTLBytesSerializedSize($fieldName)"
            "Bool" -> "SIZE_BOOLEAN"
            else -> "$fieldName.computeSerializedSize()"
        }
    }
    else -> throw RuntimeException("Unsupported type $fieldTlType")
}