if (typeof google !== 'undefined') {
  initPlayground(google)
} else {
  console.log(`Looks like loading of Google's IMA SDK are blocked by your Ad blocker.`);
  console.log(`Please Turn Off Your Ad Blocker.`);
}

function getGVastAndPlay(imaPlayer, baseUrl) {
  const inpGVastParams = document.getElementById("inpGVastParams");
  const gVastQueryString = inpGVastParams.value ? `?${inpGVastParams.value}` : '';
  const gVastUrl = `${baseUrl}/gvast${gVastQueryString}`
  fetch(gVastUrl, {
    method: 'GET',
    cache: 'no-cache',
  }).then(response => response.text())
    .then(vastXml => {
      playVast(imaPlayer, vastXml);
    }).catch((error) => {
        console.error('Error:', error);
    });
}

function doAuctionAndPlay(imaPlayer, baseUrl) {
  const txtRequestBody = document.getElementById("txtRequestBody");
  try {
    const bidRequest = JSON.parse(txtRequestBody.value);
    if (!bidRequest?.imp?.length > 1) {
      alert("No imp found in request!");
      return;
    }
    if (bidRequest?.imp?.length > 1) {
      alert("Multiple imps in request is not supported!");
      return;
    }
  } catch (err) {
    alert("Unable to parse request body as valid json!");
    return;
  }

  const auctionUrl = `${baseUrl}/openrtb2/auction`
  fetch(auctionUrl, {
    method: "POST",
    cache: 'no-cache',
    headers: {
      'Content-Type': 'application/json'
    },
    body: txtRequestBody.value
  }).then(response => response.json())
    .then(data => {
      const vastXml = data?.seatbid?.[0]?.bid?.[0]?.adm;
      if (!vastXml) {
        alert("No bid with adm found in response");
      }
      return vastXml;
    }).then(vastXml => {
      playVast(imaPlayer, vastXml);
    }).catch((error) => {
        console.error('Error:', error);
    });
}

function playVast(imaPlayer, vastXml) {
  if (vastXml) {
    const playAdsRequest = new google.ima.AdsRequest();
    playAdsRequest.adsResponse = vastXml;
    imaPlayer.playAds(playAdsRequest);
  }
}

function initPlayground(google) {
  const playGVast = document.getElementById("playGVast");
  const playButton = document.getElementById("playButton");
  const pauseButton = document.getElementById("pauseButton");
  const toggleMuteButton = document.getElementById("toggleMuteButton");
  const volumeSlider = document.getElementById("volumeSlider");
  const auctionUrl = document.getElementById("auctionUrl");
  const bidRequest = document.getElementById("bidRequest");
  const adsRenderingSettings = new google.ima.AdsRenderingSettings();

  const imaPlayer = new vastImaPlayer.Player(
    google.ima,
    document.getElementById("mediaElement"),
    document.getElementById("adContainer"),
    adsRenderingSettings
  );

  imaPlayer.addEventListener("MediaStart", () => {
    console.log("media start");
  });

  imaPlayer.addEventListener("MediaImpression", () => {
    console.log("media impression");
  });

  imaPlayer.addEventListener("MediaStop", () => {
    console.log("media stop");
  });

  imaPlayer.addEventListener("AdMetadata", () => {
    console.log("ad metadata", imaPlayer.cuePoints);
  });

  imaPlayer.addEventListener("AdStarted", (event) => {
    const adPodInfo = event.detail.ad.getAdPodInfo();
    console.log("ad started", adPodInfo);
  });

  imaPlayer.addEventListener("AdComplete", (event) => {
    const adPodInfo = event.detail.ad.getAdPodInfo();
    console.log("ad complete", adPodInfo);
  });

  // imaPlayer.addEventListener("AdProgress", () => {
  //   console.log("ad timeupdate", imaPlayer.duration, imaPlayer.currentTime);
  // });

  // imaPlayer.addEventListener("timeupdate", () => {
  //   console.log("content timeupdate", imaPlayer.duration, imaPlayer.currentTime);
  // });

  imaPlayer.addEventListener("ended", () => {
    console.log("content ended", imaPlayer.duration, imaPlayer.currentTime);
  });

  document.getElementById("mediaElement").muted = false;

  const inpBaseUrl = document.getElementById('inpBaseUrl');
  const selEnvironment = document.getElementById('selEnvironment');
  function onEnvironmentSelected() {
    if (selEnvironment.selectedOptions[0].value == 'custom') {
      inpBaseUrl.value = 'http://localhost:8080';
      inpBaseUrl.readOnly = false;
      inpBaseUrl.focus();
    } else {
      inpBaseUrl.value = selEnvironment.selectedOptions[0].value;
      inpBaseUrl.readOnly = true;
    }
  }
  selEnvironment.addEventListener("change", onEnvironmentSelected);
  onEnvironmentSelected();

  const radAuction = document.getElementById('radAuction');
  const radGVast = document.getElementById('radGVast');
  function showHideRequestFields() {
    const gVastParams = document.getElementById('gVastParams');
    const bidRequest = document.getElementById('bidRequest');
    if (radAuction.checked) {
      gVastParams.classList.add('hidden');
      bidRequest.classList.remove('hidden');
    } else {
      gVastParams.classList.remove('hidden');
      bidRequest.classList.add('hidden');
    }
  }

  [radAuction, radGVast].forEach(radio => {
    radio.addEventListener('change', showHideRequestFields);
  })

  const reHttpUrl = /^http(s)?:\/\//;

  playGVast.addEventListener("click", function () {
    let baseUrl = inpBaseUrl.value.trim();
    if (!baseUrl) {
      alert("Base url is required");
      return;
    }

    if (!reHttpUrl.test(baseUrl)) {
      alert("Invalid base url!");
      return;
    }

    baseUrl = baseUrl.replace(/\/$/);

    if (radAuction.checked) {
      doAuctionAndPlay(imaPlayer, baseUrl);
    } else {
      getGVastAndPlay(imaPlayer, baseUrl);
    }
  });

  playButton.addEventListener("click", function () {
    imaPlayer.play();
  });

  pauseButton.addEventListener("click", function () {
    imaPlayer.pause();
  });

  toggleMuteButton.addEventListener("click", function () {
    imaPlayer.muted = !imaPlayer.muted;
  });

  volumeSlider.value = String(imaPlayer.volume * 100 || 0);
  volumeSlider.addEventListener("change", function () {
    imaPlayer.volume = Number(volumeSlider.value) / 100 || 0;
  });
}
