/*
 * Copyright 2023-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.champeau.a4j.jsolex.app.jfx;

@FunctionalInterface
public interface RectangleSelectionListener {
    default boolean supports(ActionKind kind) {
        return true;
    }

    void onSelectRegion(ActionKind kind, int x, int y, int width, int height);

    enum ActionKind {
        CREATE_ANIM_OR_PANEL,
        CROP,
        IMAGEMATH_CROP,
        EXTRACT_SER_FRAMES;

        public boolean isCrop() {
            return this == CROP || this == IMAGEMATH_CROP;
        }
    }
}
