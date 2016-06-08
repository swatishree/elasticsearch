/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.action.admin.indices.rollover;

import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.test.ESIntegTestCase;

import java.util.Map;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.hamcrest.Matchers.equalTo;

@ESIntegTestCase.ClusterScope(scope = ESIntegTestCase.Scope.TEST)
public class RolloverIT extends ESIntegTestCase {

    public void testRolloverOnEmptyIndex() throws Exception {
        assertAcked(prepareCreate("test_index-1").addAlias(new Alias("test_alias")).get());
        final RolloverResponse response = client().admin().indices().prepareRolloverIndex("test_alias").get();
        assertThat(response.getOldIndex(), equalTo("test_index-1"));
        assertThat(response.getNewIndex(), equalTo("test_index-2"));
        assertThat(response.isSimulate(), equalTo(false));
        assertThat(response.isRolledOver(), equalTo(true));
        assertThat(response.isRolloverIndexCreated(), equalTo(true));
        assertThat(response.getConditionStatus().size(), equalTo(0));
        final ClusterState state = client().admin().cluster().prepareState().get().getState();
        final IndexMetaData oldIndex = state.metaData().index("test_index-1");
        assertFalse(oldIndex.getAliases().containsKey("test_alias"));
        final IndexMetaData newIndex = state.metaData().index("test_index-2");
        assertTrue(newIndex.getAliases().containsKey("test_alias"));
    }

    public void testRollover() throws Exception {
        assertAcked(prepareCreate("test_index-2").addAlias(new Alias("test_alias")).get());
        index("test_index-2", "type1", "1", "field", "value");
        flush("test_index-2");
        final RolloverResponse response = client().admin().indices().prepareRolloverIndex("test_alias").get();
        assertThat(response.getOldIndex(), equalTo("test_index-2"));
        assertThat(response.getNewIndex(), equalTo("test_index-3"));
        assertThat(response.isSimulate(), equalTo(false));
        assertThat(response.isRolledOver(), equalTo(true));
        assertThat(response.isRolloverIndexCreated(), equalTo(true));
        assertThat(response.getConditionStatus().size(), equalTo(0));
        final ClusterState state = client().admin().cluster().prepareState().get().getState();
        final IndexMetaData oldIndex = state.metaData().index("test_index-2");
        assertFalse(oldIndex.getAliases().containsKey("test_alias"));
        final IndexMetaData newIndex = state.metaData().index("test_index-3");
        assertTrue(newIndex.getAliases().containsKey("test_alias"));
    }

    public void testRolloverWithIndexSettings() throws Exception {
        assertAcked(prepareCreate("test_index-2").addAlias(new Alias("test_alias")).get());
        index("test_index-2", "type1", "1", "field", "value");
        flush("test_index-2");
        final Settings settings = Settings.builder()
            .put(IndexMetaData.SETTING_NUMBER_OF_SHARDS, 1)
            .put(IndexMetaData.SETTING_NUMBER_OF_REPLICAS, 0)
            .build();
        final RolloverResponse response = client().admin().indices().prepareRolloverIndex("test_alias")
            .settings(settings).alias(new Alias("extra_alias")).get();
        assertThat(response.getOldIndex(), equalTo("test_index-2"));
        assertThat(response.getNewIndex(), equalTo("test_index-3"));
        assertThat(response.isSimulate(), equalTo(false));
        assertThat(response.isRolledOver(), equalTo(true));
        assertThat(response.isRolloverIndexCreated(), equalTo(true));
        assertThat(response.getConditionStatus().size(), equalTo(0));
        final ClusterState state = client().admin().cluster().prepareState().get().getState();
        final IndexMetaData oldIndex = state.metaData().index("test_index-2");
        assertFalse(oldIndex.getAliases().containsKey("test_alias"));
        final IndexMetaData newIndex = state.metaData().index("test_index-3");
        assertThat(newIndex.getNumberOfShards(), equalTo(1));
        assertThat(newIndex.getNumberOfReplicas(), equalTo(0));
        assertTrue(newIndex.getAliases().containsKey("test_alias"));
        assertTrue(newIndex.getAliases().containsKey("extra_alias"));
    }

    public void testRolloverSimulate() throws Exception {
        assertAcked(prepareCreate("test_index-1").addAlias(new Alias("test_alias")).get());
        index("test_index-1", "type1", "1", "field", "value");
        flush("test_index-1");
        final RolloverResponse response = client().admin().indices().prepareRolloverIndex("test_alias").simulate(true).get();
        assertThat(response.getOldIndex(), equalTo("test_index-1"));
        assertThat(response.getNewIndex(), equalTo("test_index-2"));
        assertThat(response.isSimulate(), equalTo(true));
        assertThat(response.isRolledOver(), equalTo(false));
        assertThat(response.isRolloverIndexCreated(), equalTo(false));
        assertThat(response.getConditionStatus().size(), equalTo(0));
        final ClusterState state = client().admin().cluster().prepareState().get().getState();
        final IndexMetaData oldIndex = state.metaData().index("test_index-1");
        assertTrue(oldIndex.getAliases().containsKey("test_alias"));
        final IndexMetaData newIndex = state.metaData().index("test_index-2");
        assertNull(newIndex);
    }

    public void testRolloverConditionsNotMet() throws Exception {
        assertAcked(prepareCreate("test_index-0").addAlias(new Alias("test_alias")).get());
        index("test_index-0", "type1", "1", "field", "value");
        flush("test_index-0");
        final RolloverResponse response = client().admin().indices().prepareRolloverIndex("test_alias")
            .addMaxIndexAgeCondition(TimeValue.timeValueHours(4)).get();
        assertThat(response.getOldIndex(), equalTo("test_index-0"));
        assertThat(response.getNewIndex(), equalTo("test_index-0"));
        assertThat(response.isSimulate(), equalTo(false));
        assertThat(response.isRolledOver(), equalTo(false));
        assertThat(response.isRolloverIndexCreated(), equalTo(false));
        assertThat(response.getConditionStatus().size(), equalTo(1));
        final Map.Entry<String, Boolean> conditionEntry = response.getConditionStatus().iterator().next();
        assertThat(conditionEntry.getKey(), equalTo(new MaxAgeCondition(TimeValue.timeValueHours(4)).toString()));
        assertThat(conditionEntry.getValue(), equalTo(false));
        final ClusterState state = client().admin().cluster().prepareState().get().getState();
        final IndexMetaData oldIndex = state.metaData().index("test_index-0");
        assertTrue(oldIndex.getAliases().containsKey("test_alias"));
        final IndexMetaData newIndex = state.metaData().index("test_index-1");
        assertNull(newIndex);
    }

    public void testRolloverOnExistingIndex() throws Exception {
        assertAcked(prepareCreate("test_index-0").addAlias(new Alias("test_alias")).get());
        index("test_index-0", "type1", "1", "field", "value");
        assertAcked(prepareCreate("test_index-1").get());
        index("test_index-1", "type1", "1", "field", "value");
        flush("test_index-0", "test_index-1");
        final RolloverResponse response = client().admin().indices().prepareRolloverIndex("test_alias").get();
        assertThat(response.getOldIndex(), equalTo("test_index-0"));
        assertThat(response.getNewIndex(), equalTo("test_index-1"));
        assertThat(response.isSimulate(), equalTo(false));
        assertThat(response.isRolledOver(), equalTo(true));
        assertThat(response.isRolloverIndexCreated(), equalTo(false));
        final ClusterState state = client().admin().cluster().prepareState().get().getState();
        final IndexMetaData oldIndex = state.metaData().index("test_index-0");
        assertFalse(oldIndex.getAliases().containsKey("test_alias"));
        final IndexMetaData newIndex = state.metaData().index("test_index-1");
        assertTrue(newIndex.getAliases().containsKey("test_alias"));
    }
}