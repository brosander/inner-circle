/*
    ./webpack.config.js
*/
const path = require('path');
const webpack = require('webpack');

const HtmlWebpackPlugin = require('html-webpack-plugin');
const HtmlWebpackPluginConfig = new HtmlWebpackPlugin({
  template: './src/node/index.html',
  filename: 'index.html',
  inject: 'body'
})

module.exports = {
  entry: './src/node/index.js',
  devtool: 'inline-source-map',
  devServer: {
    proxy: {
      "/api": "http://127.0.0.1:8081",
      "/static": "http://127.0.0.1:8081",
      "/assets": "http://127.0.0.1:8081",
      "/login": "http://127.0.0.1:8081"
    }
  },
  output: {
    path: path.resolve('build/dist'),
    filename: 'index_bundle.js'
  },
  module: {
    rules: [
      { test: /\.js$/, loader: 'babel-loader', exclude: /node_modules/ },
      { test: /\.jsx$/, loader: 'babel-loader', exclude: /node_modules/ },
      { test: /\.css$/, loader: 'style-loader' },
      { test: /\.css$/, loader: 'style-loader/url' },
      { test: /\.css$/, loader: 'css-loader', query: {
          modules: true,
          localIdentName: '[name]__[local]___[hash:base64:5]'
        }
      },
      { test: /\.(png|woff|woff2|eot|ttf|svg)$/, loader: 'url-loader?limit=100000' }
    ]
  },
  plugins: [
    HtmlWebpackPluginConfig,
    new webpack.ProvidePlugin({
      $: "jquery",
      jQuery: "jquery"
    })
  ]
}
