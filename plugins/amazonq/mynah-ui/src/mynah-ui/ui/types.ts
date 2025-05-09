/*!
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
export interface FeatureValue {
    boolValue?: boolean;
    doubleValue?: number;
    longValue?: number;
    stringValue?: string;
}

export class FeatureContext {
    constructor(
        public name: string,
        public variation: string,
        public value: FeatureValue
    ) {}
}

export function tryNewMap(arr: [string, FeatureContext][]) {
    try {
        return new Map(arr)
    } catch (error) {
        return new Map()
    }
}
