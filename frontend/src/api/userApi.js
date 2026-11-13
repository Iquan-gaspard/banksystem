import { request } from './http'

export const getUsers = () => request('/users')
