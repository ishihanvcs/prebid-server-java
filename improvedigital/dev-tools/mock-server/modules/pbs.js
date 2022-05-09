// Improve Digital /pbs endpoint mocks
// USAGE: Set adapters.improvedigital.endpoint to http://localhost:8989/pbs in improvedigital.yaml

const PLACEMENT_TO_BID = {
    // Native v1.1 test creative
    301: {
        ext: {
            improvedigital: {
                line_item_id: 411331,
                bidder_id: 0,
                brand_name: "",
                buying_type: "classic",
                agency_id: "0",
            },
        },
        crid: "546504",
        exp: 120,
        id: "d7c708c3-8c5e-4024-82b8-ac13fbc128f3",
        price: 1.5,
        impid: 1234,
        adm: '{"ver":"1.1","imptrackers":["https://secure.adnxs.com/imptr?id=52311&t=2","https://euc-ice.360yield.com/imp_pixel?ic=wwRNbcPghT0ptaZGGpt4GPpwxkVvOHjIvAlCxyRMG16fsiFeyM3b9n69EEoxqRu36UFRWTluQ.zNlXNxG1nqiuvUEfq9W3.E.SAzuGtB4cB8-onzOnyGpbSWK8Hub639N1pMXjdBjqVnX1gIMAEdaJhNlK6HH2SpsyoZ.hyXquhaiJFqzs0hL9VatgvhiVceMOq-EtOGygyToAihJ4neeE-q0Ow9OI.1jP9dJQng0o4wg6CRKZkrxZtqk3KNpcj9QEPdk6E2LpSG4RBt8UVlOH4OEMnTE-cDsJ64tmLjDWMV-ZTMJQpV8htN89ICKXB7T08wThMOf5iAueRIakANStgeqz.AeD02RyIcQQVoxmE0pc-An41WVeNkhAB2xzbLi.m753eZ0cpfubL9g-uQCj1PfNl9OCif6Sim1JkzXKFNHFLLd0oIMlnewKSeQ2cXKg.Ju0X3AQWsWf7ovYvHeqJz-2JPHMWJgzOk31UhNlRS.2.D3kK39GCOT27gKA=="],"assets":[{"id":11,"data":{"value":"Quantum","type":1}},{"id":0,"data":{"value":"This is the test content of a test advertisement. ","type":2}},{"id":2,"img":{"type":3,"h":500,"url":"https://s3.amazonaws.com/files.ssp.theadtech.com/media/image/sv7nqu1b/scalecrop-500x500","w":500}},{"id":1,"title":{"text":"Sample Prebid Test Title"}}],"link":{"url":"https://euc-ice.360yield.com/click/wwRNbWzlIqZOVe2tC.Y1eknlvNK7Qjq3fPe1mattIqoPgTxB8QZ5z93VycNwjpA5CVe3BLKApXegEk.P7yugF5fa476-.KsN0lbzoAgMrozk5.vdXyoVda590d8DNNAFFv8LUxBGIi2p4Ztlix48I2n.SyrgOJhGNlo8RVXqWJn7RL44RBIiHi5IwgbV1R5tveojTm7LgwVqAc1JlPhRpQk.MQQJRkwB-hNVXCGR99eBs6-mJsgfUWUG-vZ4SPuxgYqPW2qPmJUbTkP747CTPwpqB7uV.PEd2vnULQDEEYdR.rnMHGy81vZhebLQf3ky.rcy6wc2NrTCl50dQPRUtR9d99IKwo9D8yzoX6iFq8ji89GLXklT18QgxmLt01eGDDifzup-GyR1waUdWpL9PFomszt0RaT9.Lsmt.sChd8kyklPlDs-eIfy9rS6CD28OXvd9lVu3VNZhDfnnVOxQU44hWStJ8ZZoToMBWHCPV0XgaVsJ73s9nD7jefuyA==//http%3A%2F%2Fquantum-advertising.com%2Ffr%2F"},"jstracker":"<script type=\\"application/javascript\\">var js_tracker = [\'https://secure.adnxs.com/imptr?id=52312&t=1\', \'https://pixel.adsafeprotected.com/rjss/st/291611/36974035/skeleton.js?ias_adpath=[class~=ea_improve_pid_${TAG_ID}]\']</script>"}',
        cid: "196108",
    },
    // Native v1.2 creative from Criteo
    302: {
        ext: {
            improvedigital: {
                line_item_id: 318229,
                bidder_id: 0,
                brand_name: "",
                buying_type: "rtb",
                agency_id: "0",
            },
        },
        crid: "546504",
        exp: 120,
        id: "d7c708c3-8c5e-4024-82b8-ac13fbc128f3",
        price: 2,
        impid: 1234,
        adm: "{\"ver\":\"1.2\",\"assets\":[{\"id\":0,\"data\":{\"type\":2,\"value\":\"PC stôl IAN I perfektne zapadne do každej **študentskej** či **detskej izby**, ale umiestniť ho môžete aj do svojej **pracovne**. Rovné línie a atraktívna čierna farba dopĺňajú stôl moderným nádychom. Horná polica vám umožní **postaviť až dva monitory**, káble potom môžete viesť cez otvor, ktorý sa nachádza v menšom priestore za výklopnými dvierkami. Táto polica taktiež disponuje **LED osvetlením**, čo pridá na celkovej atmosfére. PC skriňu jednoducho umiestnite do patričného priestoru v ľavej časti pod...\"}},{\"id\":1,\"title\":{\"text\":\"Písací stôl IAN I čierna\"}},{\"id\":14,\"data\":{\"type\":1,\"value\":\"scontosk\"}},{\"id\":2,\"img\":{\"type\":3,\"url\":\"https://pix.eu.criteo.net/img/img?ar=1.75&c=3&cq=256&h=200&llw=350&m=2&p=1&partner=40107&q=80&r=0&u=https%3A%2F%2Fscontoeshop.vshcdn.net%2Fcontent%2Fimages%2Fproduct%2Fdefault%2Fian-i_83690.jpg&ups=1&v=3&w=350&s=ZJi3qYZdH54FXZasmJCM3LOO\",\"w\":350,\"h\":200,\"ext\":{\"imagemode\":0}}}],\"link\":{\"url\":\"https://cat.fr.eu.criteo.com/m/delivery/ckn.php?cppv=3&cpp=kumuK6PyOXLscIFVeJEwAfm_mYwiHw3TSGIjoRC18hGjE9Dlo2BUVA3xw2XVrJ10OulNqQ1Kw7jSCySRPCjtGOAr4uLOPYBCAuah5mXgVCGmRDMWP22G5Ac4wnr-YVEY2HyxSGA05fWBOicJMJDgGd__6m2zeDXORIFi3p5-yJm5INEahOj8ZFzXHTxjzzSfh2742swXiTfzXlWia0d8oXkHeQmHXTJgJuSDrz8tukliazqCOG2arGIleVTCDgrjE8K-_doJ0riG_N4PgdlrC0mkcULntW8ECMNQ_ksMKmSoQyXWVGreNBJoOfZ_-4l_jNZHyi8yLYx-FN9Tc3mco7iVs4_d1thjbNYDx55KWyIfJ35zlEKtABz6hc_iAWTMfzEqV40iqlK_2_hAjmpmnDaUGNC9G840NR2Klv6-L1vk-g6rxLQqTL2oDHpb9H837v9vQ35zgPbmbrjrGlycb4kMTYZ11g-jcassXtpGos1JG3XP&maxdest=https%3A%2F%2Fwww.sconto.sk%2Fprodukt%2Fpisaci-stol-ian-i-cierna-414299200%3Futm_term%3Dproduct%26utm_source%3Dcriteo%26utm_medium%3Drtb%26utm_campaign%3Dretargeting\"},\"eventtrackers\":[{\"event\":1,\"method\":1,\"url\":\"https://cat.fr.eu.criteo.com/m/delivery/lgn.php?cppv=3&cpp=8H7LIKPyOXLscIFVeJEwAfm_mYwiHw3TSGIjoRC18hGjE9Dlo2BUVA3xw2XVrJ10OulNqQ1Kw7jSCySRPCjtGOAr4uLOPYBCAuah5mXgVCGmRDMWP22G5Ac4wnr-YVEY2HyxSEg-wQEMg4IKoRXAsGPGFs6gKrEjEqN25u7rp8Bv2oLTfc9LwlNUU3b-JVFKohKs7WhC0GhDr9LUzIx5layac6E9QXiMyutjgHxYZU6ebLQX5_9x5hbCinXl4VI8d6XxQBAyydTBNrI21pc-OpHrRdby_Tbqr0LLWN2ESaBn-hp7duAJsqcFFFRD3OIhoBVD2Q3peP6xzdMYCgYWKpanx-QhvI9_v7UzBoMbKgfoDoD3zfuT-ggINg4iKKt2NeDeqIRe1fLc2dYlzJuG0UBkO5bGl_-5pEIyEsr1EZeRJR2opSaMz_ngaXiW-G8czAlBiw52asjvq3iOuvCO2UxIrGo&z=zDN_2Q0xNHC4zvd0pLrLSOK08o8_S5_NNDhMRA\"}],\"privacy\":\"https://privacy.eu.criteo.com/adchoices?cppv=3&cpp=VpWokEzF0xC-pQA9tx_ZT_035O9cVmxm4fsLnTeiH8Mt5qYAQQ2-or0vbMmCYuP5zGcGZL2c2F-WC2v_BbmMLUCq4NYvFlxzItty6bY5idHomtVX-_b7GNSr2JNHBREUqN0HGZBOHJ3Ge6SapHw8zXC_BMFXy7RzW7aVASYz21PvlaW_\",\"ext\":{\"privacy\":{\"imageurl\":\"https://static.criteo.net/flash/icon/nai_small.png\",\"clickurl\":\"https://privacy.eu.criteo.com/adchoices?cppv=3&cpp=VpWokEzF0xC-pQA9tx_ZT_035O9cVmxm4fsLnTeiH8Mt5qYAQQ2-or0vbMmCYuP5zGcGZL2c2F-WC2v_BbmMLUCq4NYvFlxzItty6bY5idHomtVX-_b7GNSr2JNHBREUqN0HGZBOHJ3Ge6SapHw8zXC_BMFXy7RzW7aVASYz21PvlaW_\"},\"demand\":{\"productid\":\"414299200\",\"campaignid\":181066,\"advertiserid\":36364}},\"transparency_metadata\":{\"advertisingPlatform\":{\"id\":91,\"idType\":\"IAB_GVL_ID\",\"name\":\"Criteo\"},\"targetingCategory\":{\"geoLocation\":\"GEO_LOCATION_APPROXIMATE\",\"device\":\"DEVICE_USED\",\"remarketing\":\"REMARKETING_WEBSITE_VISIT\",\"userInterests\":\"USER_INTERESTS_USED\",\"userCharacteristics\":[\"USER_CHARACTERISTICS_NOT_USED\"],\"lookalike\":\"LOOKALIKE_NOT_USED\",\"context\":\"CONTEXT_NOT_USED\",\"other\":\"OTHER_NOT_DISCLOSED\"},\"ads\":[{\"id\":\"1f9ca9cc-8682-473b-a500-873d9668edcc\",\"advertiserDomain\":\"www.sconto.sk\",\"advertiserName\":\"Sconto SK\",\"type\":\"NATIVE\"}]}}",
        cid: "196108",
    }
};

module.exports = function (req, res) {
    const body = req.body;
    let response = {
        id: body.id,
        cur: "USD",
        seatbid: [
            {
                bid: [],
                seat: "improvedigital",
            },
        ],
    };
    for (let i = 0; i < body.imp.length; i++) {
        const placementId = body.imp[i]?.ext?.bidder?.placementId;
        if (!placementId) {
            response = { error: `placementId required` };
            break;
        }
        const bid = PLACEMENT_TO_BID[placementId];
        if (!bid) {
            response = { error: `Mock not defined for placementId '${placementId}'` };
            break;
        }
        bid.impid = body.imp[i].id;
        response.seatbid[0].bid.push(bid);
    }
    res.send(response);
};
