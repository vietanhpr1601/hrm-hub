import axios from './HttpService';
import ConstantList from '../appConfig';

const path = `${ConstantList.API_ENPOINT}/access-control`;
const data = async (request) => {
  const response = await request;
  if (response.data?.status >= 400) throw new Error(response.data.message || 'Không thể lưu phân quyền');
  return response.data.data;
};
export const getPermissionCatalog = () => data(axios.get(`${path}/catalog`));
export const getPermissionGroups = () => data(axios.get(`${path}/groups`));
export const saveRolePermissions = (id, permissions) => data(axios.put(`${path}/roles/${id}/permissions`, { permissions }));
export const createPermissionRole = (role) => data(axios.post(`${path}/roles`, role));
export const deletePermissionRole = (id) => data(axios.delete(`${path}/roles/${id}`));
export const getAccountPermissions = (id) => data(axios.get(`${path}/users/${id}`));
export const saveAccountPermissions = (id, changes) => data(axios.put(`${path}/users/${id}`, changes));
