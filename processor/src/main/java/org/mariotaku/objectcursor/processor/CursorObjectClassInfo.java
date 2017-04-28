package org.mariotaku.objectcursor.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

import org.mariotaku.library.objectcursor.annotation.CursorField;
import org.mariotaku.library.objectcursor.annotation.CursorObject;
import org.mariotaku.library.objectcursor.converter.EmptyCursorFieldConverter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

/**
 * Created by mariotaku on 15/11/27.
 */
public class CursorObjectClassInfo {

    static final ClassName EMPTY_CONVERTER = ClassName.get(EmptyCursorFieldConverter.class);
    static final ClassName STRING = ClassName.get(String.class);

    final Elements elements;
    final TypeElement objectType;

    final ClassName objectClassName;


    final List<CursorFieldInfo> fieldInfoList;
    private final Map<String, ClassName> converterMaps;
    private final Set<TypeName> customTypes;
    final boolean wantCursorIndices;
    final boolean wantValuesCreator;

    final boolean wantTableInfo;
    final Set<Element> beforeCreated, afterCreated;
    final Set<Element> beforeValueWrite, afterValueWrite;

    CursorObjectClassInfo parentClassInfo;

    public CursorObjectClassInfo(Elements elements, TypeElement objectType) {
        this.elements = elements;
        this.objectType = objectType;
        objectClassName = ClassName.get(objectType);
        final CursorObject annotation = objectType.getAnnotation(CursorObject.class);
        wantCursorIndices = annotation.cursorIndices();
        wantValuesCreator = annotation.valuesCreator();
        wantTableInfo = annotation.tableInfo();
        fieldInfoList = new ArrayList<>();
        customTypes = new HashSet<>();
        converterMaps = new HashMap<>();
        beforeCreated = new HashSet<>();
        afterCreated = new HashSet<>();
        beforeValueWrite = new HashSet<>();
        afterValueWrite = new HashSet<>();
    }

    public static ClassName getSuffixedClassName(Elements elements, TypeElement cls, String suffix) {
        final String packageName = String.valueOf(elements.getPackageOf(cls).getQualifiedName());
        final String binaryName = String.valueOf(elements.getBinaryName(cls));
        return ClassName.get(packageName, binaryName.substring(packageName.length() + 1) + suffix);
    }

    public ClassName getConverter(String name, boolean findParent) {
        ClassName converter = converterMaps.get(name);
        if (findParent && converter == null && parentClassInfo != null) {
            converter = parentClassInfo.getConverter(name, true);
        }
        return converter;
    }

    public CursorFieldInfo addField(VariableElement field) {
        if (field.getKind() != ElementKind.FIELD) throw new AssertionError();
        final Set<Modifier> modifiers = field.getModifiers();
        if (modifiers.contains(Modifier.STATIC)) {
            throw modifierNotAllowed(Modifier.STATIC, field);
        }
        if (modifiers.contains(Modifier.PRIVATE)) {
            throw modifierNotAllowed(Modifier.PRIVATE, field);
        }
        if (modifiers.contains(Modifier.PROTECTED)) {
            throw modifierNotAllowed(Modifier.PROTECTED, field);
        }
        final CursorFieldInfo fieldInfo = new CursorFieldInfo(elements, field);

        ClassName converterName;
        try {
            converterName = (ClassName) TypeName.get(fieldInfo.annotation.converter());
        } catch (MirroredTypeException mte) {
            converterName = (ClassName) TypeName.get(mte.getTypeMirror());
        }

        if (!EMPTY_CONVERTER.equals(converterName)) {
            // Custom data type
            converterMaps.put(fieldInfo.objectFieldName, converterName);
            customTypes.add(fieldInfo.type);
        }
        fieldInfoList.add(fieldInfo);
        return fieldInfo;
    }

    private IllegalArgumentException modifierNotAllowed(Modifier modifier, VariableElement field) {
        throw new IllegalArgumentException(modifier + " field is not allowed for " + objectClassName + "." + field);
    }

    public TypeMirror getSuperclass() {
        return objectType.getSuperclass();
    }

    public String getPackageName() {
        return objectClassName.packageName();
    }

    public void addBeforeCreated(Element element) {
        beforeCreated.add(element);
    }

    public void addAfterCreated(Element element) {
        afterCreated.add(element);
    }

    public void addBeforeValueWrite(Element element) {
        beforeValueWrite.add(element);
    }

    public void addAfterValueWrite(Element element) {
        afterValueWrite.add(element);
    }

    public Set<ClassName> getCustomConverters() {
        return new HashSet<>(converterMaps.values());
    }

    public Set<TypeName> getCustomTypes() {
        return customTypes;
    }

    public Set<Element> getBeforeCreated() {
        return beforeCreated;
    }

    public Set<Element> getAfterCreated() {
        return afterCreated;
    }

    public List<CursorFieldInfo> getFieldInfoList() {
        return fieldInfoList;
    }

    public List<CursorFieldInfo> getFieldInfoListIncludingParents() {
        List<CursorFieldInfo> result = new ArrayList<>();
        if (parentClassInfo != null) {
            result.addAll(parentClassInfo.getFieldInfoListIncludingParents());
        }
        result.addAll(fieldInfoList);
        return result;
    }

    public void completeParentInfo(HashMap<TypeName, CursorObjectClassInfo> classes) {
        CursorObjectClassInfo info = classes.get(TypeName.get(getSuperclass()));
        if (info == null) return;
        parentClassInfo = info;
        info.completeParentInfo(classes);
    }

    public boolean hasParentClassInfo() {
        return parentClassInfo != null;
    }

    public static class CursorFieldInfo {

        final String objectFieldName;
        final String objectFieldGetter;
        final String objectFieldSetter;

        final String indexFieldName;
        final String columnName;
        final CursorField annotation;
        final boolean nonNull;

        final boolean useGetter;
        final boolean useSetter;


        final TypeName type;

        public CursorFieldInfo(Elements elements, VariableElement field) {
            type = TypeName.get(field.asType());
            annotation = field.getAnnotation(CursorField.class);
            nonNull = hasAnnotation(elements, field.getAnnotationMirrors(), "android.support.annotation.NonNull");
            columnName = annotation.value();
            objectFieldName = String.valueOf(field.getSimpleName());
            objectFieldGetter = Utils.getGetter(field, elements);
            objectFieldSetter = Utils.getSetter(field, elements);
            useGetter = annotation.useGetter();
            useSetter = annotation.useSetter();
            if (annotation.indexFieldName().length() > 0) {
                indexFieldName = annotation.indexFieldName().replace('.', '_');
            } else {
                indexFieldName = objectFieldName;
            }
        }

        private boolean hasAnnotation(Elements elements, List<? extends AnnotationMirror> list, String name) {
            if (list == null) return false;
            Element actionElement = elements.getTypeElement(name);
            TypeMirror actionType = actionElement.asType();
            for (AnnotationMirror mirror : list) {
                final DeclaredType annotationType = mirror.getAnnotationType();
                if (annotationType.equals(actionType)) {
                    return true;
                }
            }
            return false;
        }

        public boolean useGetter() {
            return useGetter && objectFieldGetter != null;
        }

        public boolean useSetter() {
            return useSetter && objectFieldSetter != null;
        }

        @Override
        public String toString() {
            return "FieldInfo{" +
                    "type=" + type +
                    ", columnName='" + columnName + '\'' +
                    '}';
        }

        public String getReadAccessCode() {
            if (useGetter()) return objectFieldGetter + "()";
            return objectFieldName;
        }
    }
}
