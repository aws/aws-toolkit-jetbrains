//@ts-check

'use strict';

const path = require('path');

/**@type {import('webpack').Configuration}*/
const connectorAdapter = {
    target: 'web',
    entry: './src/mynah-ui/connectorAdapter.ts',
    output: {
        path: path.resolve(__dirname, 'build/assets/js'),
        filename: 'connectorAdapter.js',
        library: 'connectorAdapter',
        libraryTarget: 'var',
        devtoolModuleFilenameTemplate: '../[resource-path]',
    },
    devtool: 'source-map',
    resolve: {
        extensions: ['.ts', '.js', '.wasm'],
        fallback: {
            fs: false,
            path: false,
            util: false
        },
    },
    experiments: { asyncWebAssembly: true },
    module: {
        rules: [
            {test: /\.(sa|sc|c)ss$/, use: ['style-loader', 'css-loader', 'sass-loader']},
            {
                test: /\.ts$/,
                exclude: /node_modules/,
                use: [
                    {
                        loader: 'ts-loader',
                    },
                ],
            },
        ],
    },
};

module.exports = [connectorAdapter];
