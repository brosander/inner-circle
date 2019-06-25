"use strict";

import 'bootstrap';
import React from 'react';
import ReactDOM from 'react-dom';
import App from './components/App.jsx';
import { Image, Video, AssetContainer, Post, PostList } from "./components/components.jsx"

var loading = false;
var posts = []
var loadPosts = function() {
  if (!loading) {
    loading = true;
    var requestUrl = "/api/v1/posts" + (posts.length > 0 ? "?beforeId=" +  posts[posts.length - 1].id : "")
    fetch(requestUrl, { credentials: "same-origin" })
      .then(function(response) {
        if (response.status == 401) {
          window.location.replace("/login")
        }
        loading = false;
        if (response.ok) {
          response.json().then(new_posts => {
            posts = posts.concat(new_posts)
            ReactDOM.render(
              React.createElement(PostList, {posts: posts}), document.getElementById('content')
            )
          })
        } else {
          console.log("Received " + response.statusText + " from " + requestUrl)
        }
      }).catch(err => {
        console.log(err)
      })
  }
}
loadPosts()

jQuery(document).ready(function(){
  document.addEventListener('scroll', function(event) {
    var downPage = document.documentElement.scrollTop + window.innerHeight
    if (document.body.scrollHeight - 200 < downPage && !loading) {
      loadPosts()
    }
  })
})
