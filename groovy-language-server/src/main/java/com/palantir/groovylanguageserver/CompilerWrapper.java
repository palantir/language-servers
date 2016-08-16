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

package com.palantir.groovylanguageserver;

import io.typefox.lsapi.Diagnostic;
import io.typefox.lsapi.Location;
import io.typefox.lsapi.SymbolInformation;
import io.typefox.lsapi.TextDocumentPositionParams;
import java.nio.file.Path;
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
     * Returns a mapping from absolute path of source file to symbols located within these source files.
     * @return the set of symbols
     */
    Map<String, Set<SymbolInformation>> getFileSymbols();


    /**
     * Returns a mapping from the class or interface name to symbols which reference those types.
     * @return the set of references
     */
    Map<String, Set<SymbolInformation>> getReferences();

    /**
     * Returns the locations of the symbols that reference the symbol defined by the given params.
     * @param params the parameters used to filter down which symbol is referenced
     * @return the set of references
     */
    Set<Location> findReferences(TextDocumentPositionParams params);

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
