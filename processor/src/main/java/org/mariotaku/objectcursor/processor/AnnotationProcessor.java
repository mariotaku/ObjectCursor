/*
 *                 Twidere - Twitter client for Android
 *
 *  Copyright (C) 2012-2015 Mariotaku Lee <mariotaku.lee@gmail.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mariotaku.objectcursor.processor;


import org.mariotaku.library.objectcursor.CursorField;
import org.mariotaku.library.objectcursor.CursorObject;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import java.util.HashSet;
import java.util.Set;

public class AnnotationProcessor extends AbstractProcessor {
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        HashSet<String> set = new HashSet<>();
        set.add(CursorObject.class.getName());
        set.add(CursorField.class.getName());
        return set;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(CursorObject.class)) {
            final TypeElement type = (TypeElement) element;
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Processing class %s" + type.getQualifiedName());
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(CursorField.class)) {
            final VariableElement var = (VariableElement) element;
            final TypeElement type = (TypeElement) var.getEnclosingElement();
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Processing field " + var.getSimpleName()
                    + " of type " + type.getSimpleName());
        }
        return false;
    }
}
