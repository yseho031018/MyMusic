const { contextBridge, ipcRenderer, webUtils } = require('electron');

contextBridge.exposeInMainWorld('desktop', {
  getSettings: () => ipcRenderer.invoke('settings:get'),
  saveSettings: (settings) => ipcRenderer.invoke('settings:set', settings),
  serverSongs: () => ipcRenderer.invoke('server:songs'),
  serverRefresh: () => ipcRenderer.invoke('server:refresh'),
  serverUpload: (paths) => ipcRenderer.invoke('server:upload', paths),
  serverDelete: (id) => ipcRenderer.invoke('server:delete', id),
  pickMp3: () => ipcRenderer.invoke('dialog:pickMp3'),
  localSongs: () => ipcRenderer.invoke('library:list'),
  importLocal: (paths) => ipcRenderer.invoke('library:import', paths),
  removeLocal: (id) => ipcRenderer.invoke('library:remove', id),
  loadLyrics: (query) => ipcRenderer.invoke('lyrics:load', query),
  pathForFile: (file) => webUtils.getPathForFile(file),
  onMediaKey: (callback) => {
    ipcRenderer.on('media-key', (_event, action) => callback(action));
  },
});
