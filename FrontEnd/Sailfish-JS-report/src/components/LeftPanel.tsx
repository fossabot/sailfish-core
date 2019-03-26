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

import { h, Component } from 'preact';
import { connect } from 'preact-redux';
import '../styles/layout.scss';
import { Panel } from '../helpers/Panel';
import { ToggleButton } from './ToggleButton';
import Action from '../models/Action';
import { ActionsList } from './ActionsList';
import AppState from '../state/AppState';
import { setLeftPane, selectCheckpoint } from '../actions/actionCreators';
import { StatusPanel } from './StatusPane';
import { ActionsListBase } from './ActionsList';
import { nextCyclicItemByIndex, prevCyclicItemByIndex } from '../helpers/array';

interface LeftPanelProps {
    panel: Panel;
    checkpointActions: Action[]
    selectedCheckpointId: number;
    statusEnabled: boolean;
    panelSelectHandler: (panel: Panel) => any;
    setSelectedCheckpoint: (checkpointAciton: Action) => any;
}

class LeftPanelBase extends Component<LeftPanelProps> {

    private actionPanel: ActionsListBase;
    
    scrollPanelToTop(panel: Panel) {
        switch (panel) {
            case Panel.Actions: {
                this.actionPanel && this.actionPanel.scrollToTop();
                break;
            }
            default: {
                return;
            }
        }
    }

    render({ panel, checkpointActions, selectedCheckpointId, statusEnabled }: LeftPanelProps) {

        const cpIndex = checkpointActions.findIndex(action => action.id == selectedCheckpointId),
            cpEnabled = checkpointActions.length != 0,
            cpRootClass = [
                "layout-body-panel-controls-left-checkpoints",
                cpEnabled ? "" : "disabled"
            ].join(' ');

        return (
            <div class="layout-body-panel">
                <div class="layout-body-panel-controls">
                    <div class="layout-body-panel-controls-panels">
                        <ToggleButton
                            isToggled={panel == Panel.Actions}
                            onClick={() => this.selectPanel(Panel.Actions)}
                            text="Actions" />
                        <ToggleButton
                            isToggled={panel == Panel.Status}
                            isDisabled={!statusEnabled}
                            onClick={statusEnabled && (() => this.selectPanel(Panel.Status))}
                            text="Status" 
                            title={statusEnabled ? null : "No status info"}/>
                    </div>
                    <div class="layout-body-panel-controls-left">
                        <div class="layout-body-panel-controls-left-checkpoints">
                            <div class={cpRootClass}>
                                <div class="layout-body-panel-controls-left-checkpoints-icon"
                                    onClick={() => this.currentCpHandler(cpIndex)}
                                    style={{ cursor: cpEnabled ? 'pointer' : 'unset' }}
                                    title={ cpEnabled ? "Scroll to current checkpoint" : null }/>
                                <div class="layout-body-panel-controls-left-checkpoints-title">
                                    <p>{cpEnabled ? "" : "No "}Checkpoints</p>
                                </div>
                                {
                                    cpEnabled ? 
                                    (
                                        [
                                            <div class="layout-body-panel-controls-left-checkpoints-btn prev"
                                                onClick={cpEnabled && (() => this.prevCpHandler(cpIndex))}/>,
                                            <div class="layout-body-panel-controls-left-checkpoints-count">
                                                <p>{cpIndex + 1} of {checkpointActions.length}</p>
                                            </div>,
                                            <div class="layout-body-panel-controls-left-checkpoints-btn next"
                                                onClick={cpEnabled && (() => this.nextCpHandler(cpIndex))}/>
                                        ]
                                    ) : null
                                }
                            </div>
                        </div>
                    </div>
                </div>
                <div class="layout-body-panel-content">
                    {this.renderPanels(panel)}
                </div>
            </div>
        )
    }

    private renderPanels(selectedPanel: Panel): JSX.Element[] {
        const actionRootClass = [
                "layout-body-panel-content-wrapper",
                selectedPanel == Panel.Actions ? "" : "disabled"
            ].join(' '),
            statusRootClass = [
                "layout-body-panel-content-wrapper",
                selectedPanel == Panel.Status ? "" : "disabled"
            ].join(' ');
    
        return [
            <div class={actionRootClass}>
                <ActionsList
                    ref={ref => this.actionPanel = ref ? ref.wrappedInstance : null}/>
            </div>,
            <div class={statusRootClass}>
                <StatusPanel/>
            </div>
        ]
    }

    private selectPanel(panel: Panel) {
        if (panel == this.props.panel) {
            this.scrollPanelToTop(panel);
        } else {
            this.props.panelSelectHandler(panel);
        }
    }

    private nextCpHandler(currentCpIndex: number) {
        this.props.setSelectedCheckpoint(nextCyclicItemByIndex(this.props.checkpointActions, currentCpIndex));
    }

    private prevCpHandler(currentCpIndex: number) {
        this.props.setSelectedCheckpoint(prevCyclicItemByIndex(this.props.checkpointActions, currentCpIndex));
    }

    private currentCpHandler(cpIndex: number) {
        const cp = this.props.checkpointActions[cpIndex];

        if (cp) {
            this.props.setSelectedCheckpoint(cp);
        }
    }
}

export const LeftPanel = connect(
    (state: AppState) => ({
        panel: state.leftPane,
        selectedCheckpointId: state.selected.checkpointActionId,
        checkpointActions: state.checkpointActions,
        statusEnabled: !!state.testCase.status.cause
    }),
    dispatch => ({
        panelSelectHandler: (panel: Panel) => dispatch(setLeftPane(panel)),
        setSelectedCheckpoint: (checkpointAciton: Action) => dispatch(selectCheckpoint(checkpointAciton))
    })
)(LeftPanelBase)