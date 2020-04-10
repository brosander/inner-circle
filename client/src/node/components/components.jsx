import React from 'react'

export class Image extends React.Component {
  render() {
    var className = this.props.fullscreen ? "full-image" : "img-thumbnail"
    if (this.props.active) {
      return <img className={className} key={this.props.asset.value} src={(this.props.fullscreen ? this.props.asset.value.location : this.props.asset.value.thumbnail)}></img>
    } else {
      return <img className={className} key={this.props.asset.value} data-lazy-load-src={(this.props.fullscreen ? this.props.asset.value.location : this.props.asset.value.thumbnail)}></img>
    }
  }
}

export class Video extends React.Component {
  constructor(props) {
    super(props)
    this.state = {clicked: false}
    this.handleClick = this.handleClick.bind(this)
  }

  handleClick() {
    this.setState({clicked: true})
  }

  render() {
    if (this.state.clicked) {
      return (
        <div className={"embed-responsive embed-responsive-16by9"}>
          <video className={"embed-responsive-item"} key={this.props.asset.value.id} controls={true} autoPlay={true} >
            <source src={this.props.asset.value.location} type={"video/mp4"} />
          </video>
        </div>
      );
    } else {
      if (this.props.active) {
        return <img onClick={this.handleClick} className={"img-thumbnail"} key={this.props.asset.value.id} src={this.props.asset.value.thumbnail} />
      } else {
        return <img onClick={this.handleClick} className={"img-thumbnail"} key={this.props.asset.value.id} data-lazy-load-src={this.props.asset.value.thumbnail} />
      }
    }
  }
}

export class AssetContainer extends React.Component {
  constructor(props) {
    super(props)
    this.state = {fullscreen: false}
    this.handleClick = this.handleClick.bind(this)
    this.handleClose = this.handleClose.bind(this)
  }
  handleClick() {
    this.setState({fullscreen: true})
  }
  handleClose() {
    this.setState({fullscreen: false})
  }
  renderAsset(asset, index) {
      var childFactory = this.props.childComponentTypes[asset.type]
      var onClick = childFactory.clickable ? null : this.handleClick
      var assetElement =
        <div className={"asset-wrapper"} onClick={onClick}>
          <childFactory.component asset={asset} active={index == 0} fullscreen={this.state.fullscreen} />
        </div>
      if (this.state.fullscreen) {
        return (
          <div className={"dialog-wrapper"}>
            <div className={"dialog-controls"}>
              <a href={"/assets/" + this.props.assets[index].value.location} download={true}>
                <span className={"download"}>{String.fromCharCode(parseInt("2B73", 16))}</span>
              </a>
              <span onClick={this.handleClose} className={"close"}>x</span>
            </div>
            <div className={"dialog-content fullscreen"}>
              {assetElement}
            </div>
          </div>
        );
      }
      return assetElement
  }
  componentDidMount() {
    if (this.props.assets.length > 1) {
      // https://stackoverflow.com/a/27677524/586148
      var cHeight = 0;

      $('#' + this.props.id + (this.props.fullscreen ? "fullscreen": "")).on('slide.bs.carousel', function (e) {
          var children = e.relatedTarget.parentElement.childNodes

          var activeItem = children[e.from]

          var nextItem = children[e.to]
          var nextImage = $(nextItem.getElementsByTagName('img')[0])

          //$(nextItem).height($(activeItem).height())

          var src = nextImage.data('lazy-load-src')

          if (typeof src !== "undefined" && src != "") {
             nextImage.attr('src', src)
             nextImage.data('lazy-load-src', '');
          }
      });
    }
  }
  render() {
    var container;
    if (this.props.assets.length == 1) {
      container = this.renderAsset(this.props.assets[0], 0)
    } else {
      var carouselId = this.props.id
      var carouselClass = "carousel-control"
      if (this.state.fullscreen) {
        carouselClass = "fullscreen " + carouselClass
        carouselId += "fullscreen"
      }
      var carouselId = this.props.id
      container = (
        <div id={carouselId} className={"carousel slide"} data-ride={"carousel"} data-interval={false}>
          <ol className={"carousel-indicators"}>
            {this.props.assets.map(function(asset, index){
              return <li key={asset.key.id} data-target={"#" + carouselId} data-slide-to={index.toString()} className={index == 0 ? "active" : undefined} />
            }, this)}
          </ol>
          <div className={"carousel-inner"}>
            {this.props.assets.map(function(asset, index){
              var className = "carousel-item"
              if (index == 0) {
                className += " active"
              }
              if (this.state.fullscreen) {
                className += " fullscreen"
              }
              return (
                <div key={asset.key.id} className={className}>
                  {this.renderAsset(asset, index)}
                </div>
              );
            }, this)}
          </div>
          <a className={carouselClass + '-prev'} href={"#" + carouselId} role={"button"} data-slide={"prev"}>
            <span className={"carousel-control-prev-icon"} aria-hidden="true"></span>
            <span className={"sr-only"}>Previous</span>
          </a>
          <a className={carouselClass + '-next'} href={"#" + carouselId} role={"button"} data-slide={"next" }>
            <span className={"carousel-control-next-icon"} aria-hidden="true"></span>
            <span className={"sr-only"}>Next</span>
          </a>
        </div>
      );
    }
    if (this.state.fullscreen) {
      return (
        <div>
          <dialog className={"cover"} open={true}>
            {container}
          </dialog>
        </div>
      );
    } else {
      return container;
    }
  }
}

export class Post extends React.Component {
  render() {
    var assets = this.props.post.images.map(function(image) {
      return {"type": "image", "value": image, "key": image}
    }).concat(this.props.post.videos.map(function(video){
      return {"type":"video", "value": video, "key":video
    }}));

    return (
      <div className={"post"}>
        <div className={"poster"}>
          <img className={"smallProfileImage"} src={this.props.post.user.image}/>
          <div className={"smallProfileName"}>{this.props.post.user.name}</div>
        </div>
        <div className={"postText"}>{this.props.post.text}</div>
        <AssetContainer
            id={'a' + this.props.post.id}
            assets={assets} childComponentTypes={{
              image: {component: Image, clickable: false},
              video: {component: Video, clickable: true}}} />
        <div>
          {this.props.post.comments.map(function(comment, index) {
            var commentClass = (index == 0) ? "commentFirst" : "comment";
            return (
                <div key={comment.user + comment.text} className={commentClass}>
                  <img className={"smallProfileImage"} src={comment.user.image}/>
                  <span className={"commentUserName"}>{comment.user.name}: </span>
                  <span>{comment.text}</span>
                </div>
              );
            })
          }
        </div>
      </div>
    );
  }
}

export class PostList extends React.Component {
  render() {
    return (
      <div className={"posts"}>
        {this.props.posts.map(function(post, index){
          return (
            <div key={post.id} className={"postWrapper"}>
              <Post post={post}/>
            </div>
          )
        })}
      </div>
    );
  }
}

PostList.defaultProps = {
  posts: []
}
