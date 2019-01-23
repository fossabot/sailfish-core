/*******************************************************************************
 * Copyright 2009-2019 Exactpro (Exactpro Systems Limited)
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
 ******************************************************************************/
package com.exactpro.sf.storage.impl;

import static com.exactpro.sf.storage.impl.JSONSerializer.of;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.io.FilenameUtils.concat;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.exactpro.sf.configuration.workspace.FolderType;
import com.exactpro.sf.configuration.workspace.IWorkspaceDispatcher;
import com.exactpro.sf.configuration.workspace.WorkspaceSecurityException;
import com.exactpro.sf.storage.ISerializer;
import com.exactpro.sf.storage.StorageException;
import com.fasterxml.jackson.core.type.TypeReference;

public class FileVariableSetStorage extends AbstractVariableSetStorage {
    private static final ISerializer<Map<String, Map<String, String>>> SERIALIZER = of(new TypeReference<Map<String, Map<String, String>>>() {});
    private static final String VARIABLE_SETS_FILE_NAME = "variable-sets.json";

    private final String path;
    private final IWorkspaceDispatcher dispatcher;
    private final Map<String, Map<String, String>> variableSets;

    public FileVariableSetStorage(String path, IWorkspaceDispatcher dispatcher) {
        this.path = requireNonNull(path, "path cannot be null");
        this.dispatcher = requireNonNull(dispatcher, "dispatcher cannot be null");

        try {
            if(dispatcher.exists(FolderType.ROOT, path, VARIABLE_SETS_FILE_NAME)) {
                File file = dispatcher.getFile(FolderType.ROOT, path, VARIABLE_SETS_FILE_NAME);

                try {
                    this.variableSets = SERIALIZER.deserialize(file);
                } catch(Exception e) {
                    throw new StorageException("Failed to deserialize variable sets file: " + file.getCanonicalPath());
                }
            } else {
                this.variableSets = new HashMap<>();
            }
        } catch(WorkspaceSecurityException | IOException e) {
            throw new StorageException("Failed to initialize file: " + concat(path, VARIABLE_SETS_FILE_NAME), e);
        }
    }

    @Override
    public Map<String, String> get(String name) {
        return variableSets.get(checkName(name));
    }

    private void save() {
        try {
            File file = dispatcher.createFile(FolderType.ROOT, true, path, VARIABLE_SETS_FILE_NAME);
            SERIALIZER.serialize(variableSets, file);
        } catch(Exception e) {
            throw new StorageException("Failed to save variable sets", e);
        }
    }

    @Override
    public void put(String name, Map<String, String> variableSet) {
        variableSets.put(checkName(name), checkVariableSet(variableSet));
        save();
    }

    @Override
    public void remove(String name) {
        if(variableSets.remove(checkName(name)) == null) {
            throw new StorageException("Variable set does not exist: " + name);
        }

        save();
    }

    @Override
    public boolean exists(String name) {
        return variableSets.containsKey(checkName(name));
    }

    @Override
    public Set<String> list() {
        return new TreeSet<>(variableSets.keySet());
    }
}
