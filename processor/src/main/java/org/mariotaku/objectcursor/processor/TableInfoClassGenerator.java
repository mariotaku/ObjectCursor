package org.mariotaku.objectcursor.processor;

import com.squareup.javapoet.*;
import org.mariotaku.library.objectcursor.annotation.CursorField;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Modifier;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by mariotaku on 16/3/31.
 */
public class TableInfoClassGenerator {
    public static final String TABLE_INFO_SUFFIX = "TableInfo";

    private final CursorObjectClassInfo objectClassInfo;
    private final ClassName infoClassName;
    private final String infoClassNameWithoutPackage;

    public TableInfoClassGenerator(CursorObjectClassInfo objectClassInfo, Elements elements) {
        this.objectClassInfo = objectClassInfo;
        final String packageName = String.valueOf(elements.getPackageOf(objectClassInfo.objectType).getQualifiedName());
        final String binaryName = String.valueOf(elements.getBinaryName(objectClassInfo.objectType));
        infoClassNameWithoutPackage = binaryName.substring(packageName.length() + 1) + TABLE_INFO_SUFFIX;
        infoClassName = ClassName.get(packageName, infoClassNameWithoutPackage);
    }

    void writeContent(Appendable appendable, Elements elements, Types types) throws IOException {
        final TypeSpec.Builder builder = TypeSpec.classBuilder(infoClassNameWithoutPackage);

        builder.addModifiers(Modifier.PUBLIC);

        builder.addField(getColumnsField());

        builder.addField(getTypesField());

        JavaFile.builder(objectClassInfo.getPackageName(), builder.build()).build().writeTo(appendable);
    }

    private FieldSpec getColumnsField() {
        final FieldSpec.Builder builder = FieldSpec.builder(String[].class, "COLUMNS", Modifier.PUBLIC, Modifier.STATIC,
                Modifier.FINAL);
        final CodeBlock.Builder init = CodeBlock.builder();
        init.add("{\n");
        init.indent();
        Set<String> columnNames = new HashSet<>();
        for (CursorObjectClassInfo.CursorFieldInfo fieldInfo : objectClassInfo.fieldInfoList) {
            if (!columnNames.add(fieldInfo.columnName)) {
                throw new DuplicateColumnException(String.format("Duplicate column %s.%s -> %s",
                        objectClassInfo.objectClassName, fieldInfo.objectFieldName, fieldInfo.columnName));
            }
            init.add("$S, // $L.$L\n", fieldInfo.columnName, objectClassInfo.objectClassName, fieldInfo.objectFieldName);
        }
        init.unindent();
        init.add("}");
        builder.initializer(init.build());
        return builder.build();
    }

    private FieldSpec getTypesField() {
        final FieldSpec.Builder builder = FieldSpec.builder(String[].class, "TYPES", Modifier.PUBLIC, Modifier.STATIC,
                Modifier.FINAL);
        final CodeBlock.Builder init = CodeBlock.builder();
        init.add("{\n");
        init.indent();
        for (CursorObjectClassInfo.CursorFieldInfo fieldInfo : objectClassInfo.fieldInfoList) {
            init.add("$S, // $L\n", getColumnType(fieldInfo), fieldInfo.columnName);
        }
        init.unindent();
        init.add("}");
        builder.initializer(init.build());
        return builder.build();
    }

    private String getColumnType(CursorObjectClassInfo.CursorFieldInfo fieldInfo) {
        String columnType = fieldInfo.annotation.type();
        if (!CursorField.AUTO.equals(columnType)) return columnType;
        TypeName fieldType = fieldInfo.type;
        if (fieldType == TypeName.BOOLEAN) {
            return CursorField.INTEGER;
        } else if (fieldType == TypeName.INT) {
            return CursorField.INTEGER;
        } else if (fieldType == TypeName.LONG) {
            return CursorField.INTEGER;
        } else if (fieldType == TypeName.FLOAT) {
            return CursorField.FLOAT;
        } else if (fieldType == TypeName.DOUBLE) {
            return CursorField.FLOAT;
        } else if (fieldType == TypeName.SHORT) {
            return CursorField.INTEGER;
        } else if (fieldType == TypeName.BYTE) {
            return CursorField.INTEGER;
        } else if (fieldType == TypeName.CHAR) {
            return CursorField.INTEGER;
        } else if (fieldType.equals(CursorObjectClassInfo.STRING)) {
            return fieldInfo.nonNull ? CursorField.TEXT_NOT_NULL : CursorField.TEXT;
        } else {
            final ClassName converterClass = objectClassInfo.getConverter(fieldInfo.objectFieldName);
            if (converterClass != null) {
                return fieldInfo.nonNull ? CursorField.TEXT_NOT_NULL : CursorField.TEXT;
            } else if (fieldType instanceof ArrayTypeName) {
                if (((ArrayTypeName) fieldType).componentType == TypeName.BYTE) {
                    return CursorField.BLOB;
                }
            }
        }
        throw new UnsupportedFieldTypeException(String.format("Unsupported type %s in %s.%s", fieldInfo.type,
                objectClassInfo.objectClassName, fieldInfo.objectFieldName));
    }

    public void saveValuesCreatorFile(Filer filer, Elements elements, Types types) throws IOException {
        JavaFileObject fileObj = filer.createSourceFile(infoClassName.toString());
        try (Writer writer = fileObj.openWriter()) {
            writeContent(writer, elements, types);
            writer.flush();
        }
    }
}
