import { useStore } from 'zustand';
import useAuthStore from './useAuthStore';
import * as api from '../services/notificationService';
import { createNotificationStore } from './createNotificationStore.js';

const store = createNotificationStore(api);
const syncSession = session => store.getState().setSession(session);
syncSession(useAuthStore.getState());
useAuthStore.subscribe(syncSession);
export default function useNotificationStore(selector) { return useStore(store, selector); }
