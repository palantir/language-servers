/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.ls.groovy;

import io.typefox.lsapi.Diagnostic;
import io.typefox.lsapi.FileEvent;
import io.typefox.lsapi.ReferenceParams;
import io.typefox.lsapi.SymbolInformation;
import io.typefox.lsapi.TextDocumentContentChangeEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface CompilerWrapper {

    /**
     * Returns the root of the compiled workspace.
     */
    Path getWorkspaceRoot();

    /**
     * Compiles all relevant files in the workspace.
     * @return the compilation warnings and errors
     */
    Set<Diagnostic> compile();

    /**
     * Handle adding incremental changes to open files to be included in compilation.
     */
    void handleFileChanged(Path originalFile, List<TextDocumentContentChangeEvent> contentChanges);

    /**
     * Handle removing non-saved changes to open files from compiled source files.
     * @param originalFile the absolute file path of the original file
     */
    void handleFileClosed(Path originalFile);

    /**
     * Handle saving the accumulated changes to the origin source.
     * @param originalFile the absolute file path of the original file
     */
    void handleFileSaved(Path originalFile);

    /**
     * Handles reconfiguring the compiled files in the event some files were created, changed or deleted outside of the
     * language server.
     */
    void handleChangeWatchedFiles(List<? extends FileEvent> changes);

    /**
     * Returns a mapping from absolute path of source file to symbols located within these source files.
     */
    Map<String, Set<SymbolInformation>> getFileSymbols();

    /**
     * Returns a mapping from a type name (class, interface, or enum) to symbols which reference those types.
     */
    Map<String, Set<SymbolInformation>> getTypeReferences();

    /**
     * Returns the locations of the symbols that reference the symbol defined by the given params.
     * @param params the parameters used to filter down which symbol is referenced
     * @return the set of locations
     */
    Set<SymbolInformation> findReferences(ReferenceParams params);

    /**
     * Returns a list of symbols filtered based on a wildcard query.
     *
     * The character * designates zero or more of any character. The character ? designates exactly one character.
     *
     * @param query the query
     * @return the set of symbols
     */
    Set<SymbolInformation> getFilteredSymbols(String query);

}
