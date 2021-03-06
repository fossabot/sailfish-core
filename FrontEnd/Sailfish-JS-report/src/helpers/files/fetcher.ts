/******************************************************************************
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

/**
 * This fucntion fetches jsonp data from js file.
 * @param path path to jsonp file.
 * @param jsonpPath callback path for jsonp file.
 */
export async function fetchJsonp(path: string, jsonpPath: string): Promise<unknown> {
    return new Promise((resolve, reject) => {
        const jsonpLoader = document.createElement('script');
        
        jsonpLoader.src = path;
        jsonpLoader.async = true;

        window[jsonpPath] = (data: unknown) => {
            // reset handler
            delete window[jsonpPath];

            resolve(data);
        }

        jsonpLoader.onload = () => {
            document.body.removeChild(jsonpLoader);
        }

        jsonpLoader.onerror = err => {
            reject(err);
        }

        document.body.appendChild(jsonpLoader);
    });
}

/**
 * This funciton can be used to fetch updates from jsonp file with multiple jsonp callbacks. 
 * It accumulates updates for each jsonp callback path in array.
 * It uses prev and current update indexes to determine which of jsonp callback calls is with new update or not. 
 * 
 * @param filePath path to jsonp file.
 * @param jsonpPaths array of all jsonp callback's paths.
 * @param prevIndex previous update index.
 * @param currentIndex current update index.
 * @returns object, where keys is jsonp callback's paths, and values are arrays of updated data.
 * 
 * @example
 *      // jsonp file with 
 * 
 *      window.loadAction(1, {
 *          // some action info
 *      });
 * 
 *      window.loadAction(2, {
 *          // some another action info
 *      })
 * 
 *      window.loadMessage(3, {
 *          // some message info
 *      })
 * 
 *      // Promise will be resolved to
 *      {
 *          loadAction: [{ action }, { anotherAction }],
 *          loadMessage: [{ message }]
 *      }
 */
export async function fetchUpdate<T extends {}>(
    filePath: string, 
    jsonpPaths: string[], 
    currentIndex: number,
    prevIndex: number = Number.MIN_SAFE_INTEGER
) {
    return new Promise<T>((resolve, reject) => {
        const loader = document.createElement('script'),
            result = {} as T;

        loader.src = filePath;
        loader.async = true;

        const jsonpHandler = (jsonpPath: string) => (index: number, data: unknown) => {
            if (index < prevIndex) {
                // ignore update
                return;
            }

            result[jsonpPath].push(data);

            // final update
            if (index === currentIndex) {
                jsonpPaths.forEach(path => delete window[path]);
                resolve(result);
            }
        }

        jsonpPaths.forEach((jsonpPath: string) => {
            result[jsonpPath] = [];
            window[jsonpPath] = jsonpHandler(jsonpPath);
        });

        loader.onload = () => {
            document.body.removeChild(loader);
        }

        loader.onerror = (err) => {
            document.body.removeChild(loader);
            reject(err);
        }

        document.body.appendChild(loader);
    });
}

export type FileWatchEvent = {
    type: 'data',
    data: unknown
} | {
    type: 'error',
    err?: Error
}

export type FileWatchCallback = (e: FileWatchEvent) => unknown;

/**
 * This async function can be used to watch to changes in jsonp file. 
 * It retuns Promise, that resolves to true when file exists, otherwise it resolves to false. 
 * @param filePath path to target jsonp file.
 * @param jsonpPath callback path for jsonp file.
 * @param interval interval for check updates of the file.
 * @param cb Watch callback - it recieves event of 2 types: 
 *  data - fires each time when file was loaded,
 *  error - fires when something goes wrong, e.g. file was not found.
 * @returns interval id.
 */
export function watchFile(filePath: string, jsonpPath: string, interval: number, cb: FileWatchCallback) {
    return setInterval(() => {

        fetchJsonp(filePath, jsonpPath)
            .then(data => cb({ type: 'data', data }))
            .catch(err => cb({ type: 'error', err }));

    }, interval);
}
