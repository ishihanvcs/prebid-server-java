package com.improvedigital.prebid.server.auction.model;

import org.junit.Test;
import org.prebid.server.VertxTest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class CustParamsTest extends VertxTest {

    @Test
    public void constructFromParamsWhenTnlAssetIdIsAbsent() {
        CustParams custParams = new CustParams("tnl_pid=P%2017100600022&fp=0.01");

        assertThat(custParams.size()).isEqualTo(3);

        assertThat(custParams.get("tnl_pid").size()).isEqualTo(1);
        assertThat(custParams.get("tnl_pid").contains("P 17100600022")).isTrue();

        assertThat(custParams.get("tnl_asset_id").size()).isEqualTo(1);
        assertThat(custParams.get("tnl_asset_id").contains("prebidserver")).isTrue();

        assertThat(custParams.get("fp").size()).isEqualTo(1);
        assertThat(custParams.get("fp").contains("0.01")).isTrue();
    }

    @Test
    public void constructFromParamsWhenTnlAssetIdIsPresent() {
        CustParams custParams = new CustParams("tnl_pid=P%2017100600022&tnl_asset_id=game_preroll&fp=0.01");

        assertThat(custParams.size()).isEqualTo(3);

        assertThat(custParams.get("tnl_pid").size()).isEqualTo(1);
        assertThat(custParams.get("tnl_pid").contains("P 17100600022")).isTrue();

        assertThat(custParams.get("tnl_asset_id").size()).isEqualTo(1);
        assertThat(custParams.get("tnl_asset_id").contains("game_preroll")).isTrue();

        assertThat(custParams.get("fp").size()).isEqualTo(1);
        assertThat(custParams.get("fp").contains("0.01")).isTrue();
    }

    @Test
    public void constructFromParamsWhenTnlAssetIdIsPresentWithMultipleValues1() {
        CustParams custParams = new CustParams("tnl_asset_id=game_preroll,abc&fp=0.01");

        assertThat(custParams.size()).isEqualTo(2);

        Set<String> tnlAssetId = custParams.get("tnl_asset_id");
        assertThat(tnlAssetId.size()).isEqualTo(1);
        assertThat(tnlAssetId.contains("game_preroll") || tnlAssetId.contains("abc")).isTrue();

        assertThat(custParams.get("fp").size()).isEqualTo(1);
        assertThat(custParams.get("fp").contains("0.01")).isTrue();
    }

}
