import { RouterState } from 'connected-react-router';
import { createSelector } from 'reselect';

import { GlobalState } from '../../app';

import { State, StateProps } from './reducer';

const selectApp = (state: GlobalState): State => state.app;

export const selectRoute = (state: GlobalState): RouterState => state.router;

export interface SelectedProps extends StateProps {
  readonly route: RouterState;
}

export default createSelector(
  selectApp,
  selectRoute,
  (substate: State, route: RouterState): SelectedProps => ({
    ...substate.toObject(),
    route,
  }),
);
