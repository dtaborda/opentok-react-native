import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { View, Platform } from 'react-native';
import { createPublisher, checkAndroidPermissions, OT, removeNativeEvents, nativeEvents, setNativeEvents } from './OT';
import { sanitizeProperties, sanitizePublisherEvents } from './helpers/OTPublisherHelper';
import { handleError } from './OTError';
import OTPublisherView from './views/OTPublisherView';

class OTPublisher extends Component {
  constructor(props) {
    super(props);
    this.state = {
      publisher: null,
    };
    this.componentEvents = {
      sessionConnected: Platform.OS === 'android' ? 'session:onConnected' : 'session:sessionDidConnect',
    };
    this.componentEventsArray = Object.values(this.componentEvents);    
  }
  componentWillMount() {
    const publisherEvents = sanitizePublisherEvents(this.props.eventHandlers);
    setNativeEvents(publisherEvents);
    OT.setJSComponentEvents(this.componentEventsArray);
    this.sessionConnected = nativeEvents.addListener(this.componentEvents.sessionConnected, () => this.sessionConnectedHandler());
  }
  componentDidMount() {
    this.createPublisher();    
  }
  componentDidUpdate(previousProps) {
    const useDefault = (value, defaultValue) => (value === undefined ? defaultValue : value);
    const shouldUpdate = (key, defaultValue) => {
      const previous = useDefault(previousProps.properties[key], defaultValue);
      const current = useDefault(this.props.properties[key], defaultValue);
      return previous !== current;
    };

    const updatePublisherProperty = (key, defaultValue) => {
      if (shouldUpdate(key, defaultValue)) {
        const value = useDefault(this.props.properties[key], defaultValue);
        if (key === 'cameraPosition') {
          OT.changeCameraPosition(value);
        } else {
          OT[key](value);          
        }
      }
    };

    updatePublisherProperty('publishAudio', true);
    updatePublisherProperty('publishVideo', true);
    updatePublisherProperty('cameraPosition', 'front');
  }
  componentWillUnmount() {
    OT.destroyPublisher((error) => {
      if (error) {
        handleError(error);        
      } else {
        this.sessionConnected.remove();        
        OT.removeJSComponentEvents(this.componentEventsArray);         
        const events = sanitizePublisherEvents(this.props.eventHandlers);
        removeNativeEvents(events);
      }
    });
  }
  sessionConnectedHandler = () => {
    OT.publish((publishError) => {
      if (publishError) {
        handleError(publishError);
      } else {
        this.setState({
          publisher: true,
        })
      }
    });
  }
  createPublisher() {
    if (Platform.OS === 'android') {
      checkAndroidPermissions()
        .then(() => {
          this.initPublisher();
        })
        .catch((error) => {
          handleError(error);
        });
    } else {
      this.initPublisher();
    }
  }
  initPublisher() {
    const publisherProperties = sanitizeProperties(this.props.properties);
    OT.initPublisher(publisherProperties);
  }
  render() {
    if (this.state.publisher) {
      return <OTPublisherView {...this.props} />;
    }
    return <View />;
  }
}
const viewPropTypes = View.propTypes;
OTPublisher.propTypes = {
  ...viewPropTypes,
  properties: PropTypes.object, // eslint-disable-line react/forbid-prop-types
  eventHandlers: PropTypes.object, // eslint-disable-line react/forbid-prop-types
};
OTPublisher.defaultProps = {
  properties: {},
  eventHandlers: {},
};
export default OTPublisher;
