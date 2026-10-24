import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'uk.co.ateliermulliner.calltoartists',
  appName: 'Call to Artists',
  webDir: 'www',
  android: {
    backgroundColor: '#17130F'
  },
  plugins: {
    // Native HTTP: lets the app read listing pages without browser CORS limits.
    CapacitorHttp: { enabled: true },
    LocalNotifications: {
      smallIcon: 'ic_stat_cta',
      iconColor: '#C9A45C'
    }
  }
};

export default config;
